/*******************************************************************************
 *     SDR Trunk 
 *     Copyright (C) 2014,2015 Dennis Sheirer
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/
package io.github.dsheirer.module.decode.nbfm;

import io.github.dsheirer.channel.state.DecoderStateEvent;
import io.github.dsheirer.channel.state.IDecoderStateEventProvider;
import io.github.dsheirer.channel.state.State;
import io.github.dsheirer.dsp.filter.FilterFactory;
import io.github.dsheirer.dsp.filter.Window;
import io.github.dsheirer.dsp.filter.design.FilterDesignException;
import io.github.dsheirer.dsp.filter.fir.FIRFilterSpecification;
import io.github.dsheirer.dsp.filter.fir.complex.ComplexFIRFilter2;
import io.github.dsheirer.dsp.filter.resample.RealResampler;
import io.github.dsheirer.dsp.fm.FMDemodulator;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.PrimaryDecoder;
import io.github.dsheirer.module.decode.config.DecodeConfiguration;
import io.github.dsheirer.module.demodulate.fm.FMDemodulatorModule;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.buffer.IReusableBufferProvider;
import io.github.dsheirer.sample.buffer.IReusableComplexBufferListener;
import io.github.dsheirer.sample.buffer.ReusableComplexBuffer;
import io.github.dsheirer.sample.buffer.ReusableFloatBuffer;
import io.github.dsheirer.source.ISourceEventListener;
import io.github.dsheirer.source.SourceEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decoder module with integrated narrowband FM (12.5 or 25.0 kHz channel) demodulator
 */
public class NBFMDecoder extends PrimaryDecoder implements ISourceEventListener, IReusableComplexBufferListener,
		Listener<ReusableComplexBuffer>, IReusableBufferProvider, IDecoderStateEventProvider
{
	private final static Logger mLog = LoggerFactory.getLogger(FMDemodulatorModule.class);
	private static final double FM_CHANNEL_BANDWIDTH = 12500.0;
	private static final double DEMODULATED_AUDIO_SAMPLE_RATE = 8000.0;
	private static final double POWER_SQUELCH_ALPHA_DECAY = 0.0001;
	private static final double POWER_SQUELCH_THRESHOLD_DB = -78.0;
	private static final int POWER_SQUELCH_RAMP = 4;

	private ComplexFIRFilter2 mIQFilter;
	private FMDemodulator mDemodulator = new FMDemodulator(POWER_SQUELCH_ALPHA_DECAY, POWER_SQUELCH_THRESHOLD_DB,
			POWER_SQUELCH_RAMP);
	private RealResampler mResampler;
	private SourceEventProcessor mSourceEventProcessor = new SourceEventProcessor();
	private Listener<ReusableFloatBuffer> mResampledReusableBufferListener;
	private Listener<DecoderStateEvent> mDecoderStateEventListener;
	private double mChannelBandwidth = FM_CHANNEL_BANDWIDTH;
	private double mOutputSampleRate = DEMODULATED_AUDIO_SAMPLE_RATE;
	private boolean mSquelch = false;

	/**
	 * Constructs an instance
	 * @param config to setup the decoder
	 */
	public NBFMDecoder( DecodeConfiguration config )
	{
		super( config );
	}

	@Override
    public DecoderType getDecoderType()
    {
	    return DecoderType.NBFM;
    }

	@Override
	public Listener<ReusableComplexBuffer> getReusableComplexBufferListener()
	{
		return this;
	}

	@Override
	public Listener<SourceEvent> getSourceEventListener()
	{
		return mSourceEventProcessor;
	}

	@Override
	public void reset()
	{
		mDemodulator.reset();
	}

	@Override
	public void start()
	{
	}

	@Override
	public void stop()
	{
	}

	@Override
	public void setBufferListener(Listener<ReusableFloatBuffer> listener)
	{
		mResampledReusableBufferListener = listener;
	}

	@Override
	public void removeBufferListener()
	{
		mResampledReusableBufferListener = null;
	}

	@Override
	public void receive(ReusableComplexBuffer reusableComplexBuffer)
	{
		if(mIQFilter == null)
		{
			reusableComplexBuffer.decrementUserCount();
			throw new IllegalStateException("NBFM demodulator module must receive a sample rate change source " +
					"event before it can process complex sample buffers");
		}

		ReusableComplexBuffer basebandFilteredBuffer = mIQFilter.filter(reusableComplexBuffer);
		ReusableFloatBuffer demodulatedBuffer = mDemodulator.demodulate(basebandFilteredBuffer);

		if(mResampler != null)
		{
			//If we're currently squelched and the squelch state changed while demodulating the baseband samples,
			// then un-squelch so we can send this buffer
			if(mSquelch && mDemodulator.isSquelchChanged())
			{
				mSquelch = false;
				notifyCallStart();
			}

			//Either send the demodulated buffer to the resampler for distro, or decrement the user count
			if(mSquelch)
			{
				demodulatedBuffer.incrementUserCount();
				notifyIdle();
			}
			else
			{
				mResampler.resample(demodulatedBuffer);
				notifyCallContinuation();
			}

			//Set to squelch if necessary to close out the audio buffers
			if(!mSquelch && mDemodulator.isMuted())
			{
				mSquelch = true;
				notifyCallEnd();
			}
		}
		else
		{
			demodulatedBuffer.decrementUserCount();
			notifyIdle();
		}
	}

	private void notifyCallStart()
	{
		broadcast(new DecoderStateEvent(this, DecoderStateEvent.Event.START, State.CALL, 0));
	}

	private void notifyCallContinuation()
	{
		broadcast(new DecoderStateEvent(this, DecoderStateEvent.Event.CONTINUATION, State.CALL, 0));
	}

	private void notifyCallEnd()
	{
		broadcast(new DecoderStateEvent(this, DecoderStateEvent.Event.END, State.IDLE, 0));
	}

	private void notifyIdle()
	{
		broadcast(new DecoderStateEvent(this, DecoderStateEvent.Event.CONTINUATION, State.IDLE, 0));
	}

	/**
	 * Broadcasts the decoder state event to an optional registered listener
	 */
	private void broadcast(DecoderStateEvent event)
	{
		if(mDecoderStateEventListener != null)
		{
			mDecoderStateEventListener.receive(event);
		}
	}

	/**
	 * Sets the decoder state listener
	 */
	@Override
	public void setDecoderStateListener(Listener<DecoderStateEvent> listener)
	{
		mDecoderStateEventListener = listener;
	}

	/**
	 * Removes the decoder state event listener
	 */
	@Override
	public void removeDecoderStateListener()
	{
		mDecoderStateEventListener = null;
	}

	/**
	 * Monitors sample rate change source event(s) to setup the initial I/Q filter
	 */
	public class SourceEventProcessor implements Listener<SourceEvent>
	{
		@Override
		public void receive(SourceEvent sourceEvent)
		{
			if(sourceEvent.getEvent() == SourceEvent.Event.NOTIFICATION_SAMPLE_RATE_CHANGE)
			{
				if(mIQFilter != null)
				{
					mIQFilter.dispose();
					mIQFilter = null;
				}

				double sampleRate = sourceEvent.getValue().doubleValue();

				if((sampleRate < (2.0 * mChannelBandwidth)))
				{
					throw new IllegalStateException("FM Demodulator with channel bandwidth [" + mChannelBandwidth +
							"] requires a channel sample rate of [" + (2.0 * mChannelBandwidth + "] - sample rate of [" +
							sampleRate + "] is not supported"));
				}

				double cutoff = sampleRate / 4.0;
				int passBandStop = (int)cutoff - 500;
				int stopBandStart = (int)cutoff + 500;

				float[] filterTaps = null;

				FIRFilterSpecification specification = FIRFilterSpecification.lowPassBuilder()
						.sampleRate(sampleRate)
						.gridDensity(16)
						.oddLength(true)
						.passBandCutoff(passBandStop)
						.passBandAmplitude(1.0)
						.passBandRipple(0.01)
						.stopBandStart(stopBandStart)
						.stopBandAmplitude(0.0)
						.stopBandRipple(0.028) //Approximately 60 dB attenuation
						.build();

				try
				{
					filterTaps = FilterFactory.getTaps(specification);
				}
				catch(FilterDesignException fde)
				{
					mLog.error("Couldn't design FM demodulator remez filter for sample rate [" + sampleRate +
							"] pass frequency [" + passBandStop + "] and stop frequency [" + stopBandStart +
							"] - using sinc filter");
				}

				if(filterTaps == null)
				{
					filterTaps = FilterFactory.getLowPass(sampleRate, passBandStop, stopBandStart, 60,
							Window.WindowType.HAMMING, true);
				}

				mIQFilter = new ComplexFIRFilter2(filterTaps);

				mResampler = new RealResampler(sampleRate, mOutputSampleRate, 2000, 1000);

				mResampler.setListener(new Listener<ReusableFloatBuffer>()
				{
					@Override
					public void receive(ReusableFloatBuffer reusableFloatBuffer)
					{
						if(mResampledReusableBufferListener != null)
						{
							mResampledReusableBufferListener.receive(reusableFloatBuffer);
						}
						else
						{
							reusableFloatBuffer.decrementUserCount();
						}
					}
				});
			}
		}
	}
}
