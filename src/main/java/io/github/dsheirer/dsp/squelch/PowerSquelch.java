package io.github.dsheirer.dsp.squelch;

import io.github.dsheirer.dsp.filter.iir.SinglePoleIirFilter;
import io.github.dsheirer.sample.Listener;
import io.github.dsheirer.sample.buffer.ReusableComplexBuffer;
import io.github.dsheirer.source.wave.ComplexWaveSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;

/**
 * Power squelch
 *
 * Modeled after gnuradio complex power squelch:
 * https://github.com/gnuradio/gnuradio/blob/master/gr-analog/lib/pwr_squelch_cc_impl.cc
 */
public class PowerSquelch
{
    private static final Logger mLog = LoggerFactory.getLogger(PowerSquelch.class);
    private State mState = State.MUTE;
    private SinglePoleIirFilter mFilter;
    private double mPower = 0.0f;
    private double mThreshold;
    private int mRampThreshold;
    private int mRampCount;
    private boolean mSquelchChanged = false;

    /**
     * Constructs an instance
     *
     * Testing against a 12.5 kHz analog FM modulated signal, the following parameters provided a
     * good responsiveness.  A threshold of -80.0 dB seemed to trigger significant flapping during un-squelching.
     *   - alpha: 0.0001
     *   - threshold: 78.0 dB
     *   - ramp: 4 (samples)
     *
     * @param alpha decay value of the single pole IIR filter in range: 0.0 - 1.0.  The smaller the alpha value,
     * the slower the squelch response.
     * @param threshold in decibels.  Signal power must exceed this threshold value for unsquelch.
     * @param ramp count of samples before transition between mute and unmute.  Setting this value to zero
     * causes immediate mute and unmute.  Set to higher count to prevent mute/unmute flapping.
     */
    public PowerSquelch(double alpha, double threshold, int ramp)
    {
        mFilter = new SinglePoleIirFilter(alpha);
        setThreshold(threshold);
        mRampThreshold = ramp;
    }

    /**
     * Squelch threshold value
     * @return value in decibels
     */
    public double getThreshold()
    {
        return 10.0 * Math.log10(mThreshold);
    }

    /**
     * Sets the squelch threshold
     * @param threshold in decibels
     */
    public void setThreshold(double threshold)
    {
        mThreshold = Math.pow(10.0, threshold / 10.0);
    }

    /**
     * Processes a complex IQ sample and changes squelch state when the signal power is above or below the
     * threshold value.
     * @param inphase complex sample component
     * @param quadrature complex sample component
     */
    public void process(double inphase, double quadrature)
    {
        mPower = mFilter.filter(inphase * inphase + quadrature * quadrature);

        switch(mState)
        {
            case MUTE -> {
                if(!mute())
                {
                    if(mRampThreshold > 0)
                    {
                        mState = State.ATTACK;
                        mRampCount++;
                    }
                    else
                    {
                        mState = State.UNMUTE;
                        setSquelchChanged(true);
                    }
                }
            }
            case ATTACK -> {
                if(mRampCount >= mRampThreshold)
                {
                    mState = State.UNMUTE;
                    setSquelchChanged(true);
                }
                else
                {
                    mRampCount++;
                }
            }
            case DECAY -> {
                if(mRampCount <= 0)
                {
                    mState = State.MUTE;
                    setSquelchChanged(true);
                }
                else
                {
                    mRampCount--;
                }
            }
            case UNMUTE -> {
                if(mute())
                {
                    if(mRampThreshold > 0)
                    {
                        mState = State.DECAY;
                        mRampCount--;
                    }
                    else
                    {
                        mState = State.MUTE;
                        setSquelchChanged(true);
                    }
                }
            }
        }
    }

    /**
     * Indicates if the current state is muted
     */
    public boolean isMuted()
    {
        return mState == State.MUTE;
    }

    /**
     * Indicates if the current state is unmuted
     */
    public boolean isUnmuted()
    {
        return mState == State.UNMUTE;
    }

    /**
     * Indicates the squelch is in a ramp up attack state
     */
    public boolean isAttack()
    {
        return mState == State.ATTACK;
    }

    /**
     * Indicates the squelch is in a ramp down decay state
     */
    public boolean isDecay()
    {
        return mState == State.DECAY;
    }

    /**
     * Current power level
     * @return current power level in dB
     */
    public float getPower()
    {
        return (float)(10.0 * Math.log10(mPower));
    }

    /**
     * Indicates if the current power level is below the threshold, indicating that the state should be muted.
     */
    private boolean mute()
    {
        return mPower < mThreshold;
    }

    /**
     * Indicates if the squelch state has changed (muted > unmuted, or vice-versa)
     */
    public boolean isSquelchChanged()
    {
        return mSquelchChanged;
    }

    /**
     * Sets or resets the squelch changed flag
     */
    public void setSquelchChanged(boolean changed)
    {
        mSquelchChanged = changed;
    }

    /**
     * State of squelch processing
     */
    private enum State
    {
        ATTACK,
        DECAY,
        MUTE,
        UNMUTE;
    }

    /**
     * Simple test harness for processing raw IQ buffers from a recording
     */
    public static class SquelchTestProcessor implements Listener<ReusableComplexBuffer>
    {
        private PowerSquelch mPowerSquelch;
        private double mSampleRate = 50000.0;
        private double mSampleCount = 0.0;
        private boolean mMuted = true;
        private DecimalFormat mDecimalFormat = new DecimalFormat("0.00001");

        public SquelchTestProcessor(double alpha, double threshold, int ramp)
        {
            mPowerSquelch = new PowerSquelch(alpha, threshold, ramp);
        }

        @Override
        public void receive(ReusableComplexBuffer buffer)
        {
            float[] samples = buffer.getSamples();

            for(int x = 0; x < buffer.getSampleCount(); x++)
            {
                mSampleCount++;
                mPowerSquelch.process(samples[2 * x], samples[2 * x + 1]);

                if(mMuted)
                {
                    if(mPowerSquelch.isUnmuted())
                    {
                        mMuted = false;
                        mLog.info("Power: " + mDecimalFormat.format(mPowerSquelch.getPower()) +
                                " Samples:" + mSampleCount +
                                " Time:" + mDecimalFormat.format(mSampleCount / mSampleRate) +
                                " **UN-MUTED**");
                    }
                }
                else
                {
                    if(mPowerSquelch.isMuted())
                    {
                        mMuted = true;
                        mLog.info("Power: " + mDecimalFormat.format(mPowerSquelch.getPower()) +
                                " Samples:" + mSampleCount +
                                " Time:" + mDecimalFormat.format(mSampleCount / mSampleRate) +
                                " **MUTED**");
                    }
                }

                if(mSampleCount % 5000 == 0)
                {
                    mLog.info("Power: " + mDecimalFormat.format(mPowerSquelch.getPower()) +
                            " Samples:" + mSampleCount +
                            " Time:" + mDecimalFormat.format(mSampleCount / mSampleRate));
                }

                if(mPowerSquelch.isAttack())
                {
                    mLog.info("Power: " + mDecimalFormat.format(mPowerSquelch.getPower()) +
                            " Samples:" + mSampleCount +
                            " Time:" + mDecimalFormat.format(mSampleCount / mSampleRate) +
                            " **ATTACK**");
                }

                if(mPowerSquelch.isDecay())
                {
                    mLog.info("Power: " + mDecimalFormat.format(mPowerSquelch.getPower()) +
                            " Samples:" + mSampleCount +
                            " Time:" + mDecimalFormat.format(mSampleCount / mSampleRate) +
                            " **DECAY**");
                }
            }

            buffer.decrementUserCount();
        }
    }

    public static void main(String[] args)
    {
        mLog.info("Starting ...");
        String path = "/home/denny/SDRTrunk/recordings/NBFM_Squelch_Test_Squelch_Test_369_baseband_20211031_060024.wav";

        double alpha = 0.0001;
        double thresholdDb = -78.0;
        int ramp = 4;

        Listener<ReusableComplexBuffer> listener = new SquelchTestProcessor(alpha, thresholdDb, ramp);
        try(ComplexWaveSource source = new ComplexWaveSource(new File(path), false))
        {
            source.setListener(listener);
            source.start();
            while(true)
            {
                source.next(1000, true);
            }
        }
        catch(IOException ioe)
        {
            mLog.error("Error, or end of file", ioe);
        }

        mLog.info("Finished");
    }
}
