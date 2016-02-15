/*******************************************************************************
 *     SDR Trunk 
 *     Copyright (C) 2014-2016 Dennis Sheirer
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
package module.decode.mpt1327;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;

import message.Message;
import module.decode.DecoderType;
import module.decode.config.DecodeConfiguration;
import module.decode.event.CallEvent;
import module.decode.event.CallEvent.CallEventType;
import module.decode.mpt1327.MPT1327Message.IdentType;
import module.decode.state.ChangeChannelTimeoutEvent;
import module.decode.state.ChangedAttribute;
import module.decode.state.DecoderState;
import module.decode.state.DecoderStateEvent;
import module.decode.state.DecoderStateEvent.Event;
import module.decode.state.State;
import module.decode.state.TrafficChannelAllocationEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import alias.Alias;
import alias.AliasList;
import audio.metadata.Metadata;
import audio.metadata.MetadataType;
import controller.channel.Channel.ChannelType;
import controller.channel.map.ChannelMap;

public class MPT1327DecoderState extends DecoderState
{
	private final static Logger mLog = LoggerFactory.getLogger( MPT1327DecoderState.class );

	private TreeSet<String> mIdents = new TreeSet<String>();
	private HashMap<String,ArrayList<String>> mGroups = new HashMap<String,ArrayList<String>>();
	
	private String mSite;
	private String mFromTalkgroup;
	private String mToTalkgroup;

	private int mChannelNumber;
	private ChannelType mChannelType;
	private ChannelMap mChannelMap;
	
	private long mFrequency = 0;
	private long mCallTimeout;
	
	public MPT1327DecoderState( AliasList aliasList, 
								ChannelMap channelMap, 
								ChannelType channelType,
								long callTimeout )
	{
		super( aliasList );
		
		mChannelMap = channelMap;
		mChannelType = channelType;
		mCallTimeout = callTimeout;
	}
	
	@Override
	public DecoderType getDecoderType()
	{
		return DecoderType.MPT1327;
	}

	@Override
	public void start( ScheduledExecutorService executor )
	{
	}

	@Override
	public void stop()
	{
	}

	public ChannelType getChannelType()
	{
		return mChannelType;
	}
	
	public void dispose()
	{
		super.dispose();
	}
	
	@Override
    public void receive( Message message )
    {
		if( message.isValid() )
		{
			if( message instanceof MPT1327Message )
			{
				MPT1327Message mpt = ((MPT1327Message)message);
				
				switch( mpt.getMessageType() )
				{
					case ACK:
						mIdents.add( mpt.getFromID() );
						
						IdentType identType = mpt.getIdent1Type();
						
						if( identType == IdentType.REGI )
						{
							broadcast( new MPT1327CallEvent.Builder( CallEventType.REGISTER )
										.aliasList( getAliasList() )
										.channel( String.valueOf( mChannelNumber ) )
										.details( "REGISTERED ON NETWORK" )
										.frequency( mFrequency )
										.from( mpt.getToID() )
										.to( mpt.getFromID() )
										.build() );
						}
						else
						{
							broadcast( new MPT1327CallEvent.Builder( CallEventType.RESPONSE )
										.aliasList( getAliasList() )
										.channel( String.valueOf( mChannelNumber ) )
										.details( "ACK " + identType.getLabel() )
										.frequency( mFrequency )
										.from( mpt.getFromID() )
										.to( mpt.getToID() )
										.build() );
						}
						
						broadcast( new DecoderStateEvent( this, Event.CONTINUATION, State.CONTROL ) );
						break;
					case ACKI:
						mIdents.add( mpt.getFromID() );
						mIdents.add( mpt.getToID() );
						
						broadcast( new DecoderStateEvent( this, Event.CONTINUATION, State.CONTROL ) );
						break;
					case AHYC:
						mIdents.add( mpt.getToID() );

						broadcast( new MPT1327CallEvent.Builder( CallEventType.COMMAND )
								.aliasList( getAliasList() )
								.channel( String.valueOf( mChannelNumber ) )
								.details( ( (MPT1327Message) message ).getRequestString() )
								.frequency( mFrequency )
								.from( mpt.getFromID() )
								.to( mpt.getToID() )
								.build() );

						broadcast( new DecoderStateEvent( this, Event.CONTINUATION, State.CONTROL ) );
						break;
					case AHYQ:
						broadcast( new MPT1327CallEvent.Builder( CallEventType.STATUS )
									.aliasList( getAliasList() )
									.channel( String.valueOf( mChannelNumber ) )
									.details( mpt.getStatusMessage() )
									.frequency( mFrequency )
									.from( mpt.getFromID() )
									.to( mpt.getToID() )
									.build() );
						
						broadcast( new DecoderStateEvent( this, Event.CONTINUATION, State.CONTROL ) );
						break;
					case ALH:
						String site = mpt.getSiteID();

						if( site != null && ( mSite == null || !site.contentEquals( mSite ) ) )
						{
							mSite = site;
							broadcast( ChangedAttribute.CHANNEL_SITE_NUMBER  );
						}
						mSite = mpt.getSiteID();
						
						broadcast( new DecoderStateEvent( this, Event.START, State.CONTROL ) );
						break;
					case GTC:
						String from = mpt.getFromID();
						String to = mpt.getToID();
						
						if( from != null )
						{
							mIdents.add( from );
						}
						
						if( to != null )
						{
							mIdents.add( to );
						}

						/* Capture the idents that talk to each group */
						if( from != null && to != null )
						{
							if( mGroups.containsKey( to ) )
							{
								ArrayList<String> talkgroups = mGroups.get( to );
								
								if( !talkgroups.contains( from ) )
								{
									talkgroups.add( from );
								}
							}
							else
							{
								ArrayList<String> talkgroups = new ArrayList<String>();
								talkgroups.add( from );
								
								mGroups.put( to, talkgroups );
							}
						}

						int channel = mpt.getChannel();
						
						long frequency = 0;
						
						if( getChannelMap() != null )
						{
							frequency = getChannelMap().getFrequency( channel );
						}
						
						CallEvent event = new MPT1327CallEvent.Builder( CallEventType.CALL )
									.aliasList( getAliasList() )
									.channel( String.valueOf( channel ) )
									.details( "GTC" )
									.frequency( frequency )
									.from( mpt.getFromID() )
									.to( mpt.getToID() )
									.build();
									
						broadcast( new TrafficChannelAllocationEvent( this, event ) );
						break;
					case HEAD_PLUS1:
					case HEAD_PLUS2:
					case HEAD_PLUS3:
					case HEAD_PLUS4:
						broadcast( new MPT1327CallEvent.Builder( CallEventType.SDM )
									.aliasList( getAliasList() )
									.details( mpt.getMessage() )
									.from( mpt.getFromID() )
									.to( mpt.getToID() )
									.build() );
						broadcast( new DecoderStateEvent( this, Event.CONTINUATION, State.CONTROL ) );
						break;

					/* Traffic Channel Events */
					case CLEAR:
						mChannelNumber = mpt.getChannel();
						
						broadcast( new DecoderStateEvent( this, Event.END, State.FADE ) );
						break;
					case MAINT:
						if( mChannelType == ChannelType.STANDARD )
						{
							/**
							 * When we receive a MAINT message and we're configured
							 * as a standard channel, we need to apply the call
							 * timeout specified by the user.  Otherwise we'll
							 * be using the shorter default call timeout
							 */
							broadcast( new ChangeChannelTimeoutEvent( this, 
									mChannelType, mCallTimeout ) );

							if( mCurrentCallEvent == null )
							{
								mCurrentCallEvent = new MPT1327CallEvent.Builder( CallEventType.CALL )
										.aliasList( getAliasList() )
										.channel( String.valueOf( mChannelNumber ) )
										.details( "MONITORED TRAFFIC CHANNEL" )
										.frequency( mFrequency )
										.to( mpt.getToID() )
										.build();
								
								broadcast( mCurrentCallEvent );
							}
							
							broadcast(new Metadata(MetadataType.TO, 
									mpt.getToID(), mpt.getToIDAlias(), true));
							
							broadcast( new DecoderStateEvent( this, Event.START, State.CALL ) );

							setToTalkgroup( mpt.getToID() );
						}
						break;
					default:
						break;
				}
			}
		}
    }
	
	public void reset()
	{
		mIdents.clear();
		
		resetState();
	}
	
	private void resetState()
	{
		mFromTalkgroup = null;
		broadcast( ChangedAttribute.FROM_TALKGROUP );

		mToTalkgroup = null;
		broadcast( ChangedAttribute.TO_TALKGROUP );

		/**
		 * If this is a standard channel, reset the fade timeout to the default
		 * timeout.  Once processing is underway, if we get a MAINT message,
		 * this indicates we're processing a traffic channel as a standard 
		 * channel, so we'll issue a different call timeout then.
		 */
		if( mChannelType == ChannelType.STANDARD )
		{
			broadcast( new ChangeChannelTimeoutEvent( this, mChannelType, 
					DecodeConfiguration.DEFAULT_CALL_TIMEOUT_SECONDS * 1000 ) );

			if( mCurrentCallEvent != null )
			{
				mCurrentCallEvent.end();
				broadcast( mCurrentCallEvent );
				mCurrentCallEvent = null;
			}
		}
	}
	
	public String getSite()
	{
		return mSite;
	}
	
	public Alias getSiteAlias()
	{
		if( hasAliasList() )
		{
			return getAliasList().getSiteID( mSite );
		}
		
		return null;
	}
	
	public ChannelMap getChannelMap()
	{
		return mChannelMap;
	}
	
	public void setChannelMap( ChannelMap channelMap )
	{
		mChannelMap = channelMap;
	}
	
	public String getFromTalkgroup()
	{
		return mFromTalkgroup;
	}

	/**
	 * Set the talkgroup.  This is used primarily for traffic channels since
	 * the talkgroup will already have been identified prior to the traffic
	 * channel being created.
	 */
	public void setFromTalkgroup( String fromTalkgroup )
	{
		mFromTalkgroup = fromTalkgroup;
		
		broadcast( ChangedAttribute.FROM_TALKGROUP );
	}
	
	public Alias getFromTalkgroupAlias()
	{
		if( hasAliasList() )
		{
			return getAliasList().getMPT1327Alias( mFromTalkgroup );
		}
		
		return null;
	}
	
	public String getToTalkgroup()
	{
		return mToTalkgroup;
	}

	/**
	 * Set the talkgroup.  This is used primarily for traffic channels since
	 * the talkgroup will already have been identified prior to the traffic
	 * channel being created.
	 */
	public void setToTalkgroup( String toTalkgroup )
	{
		mToTalkgroup = toTalkgroup;
		
		broadcast( ChangedAttribute.TO_TALKGROUP );
	}
	
	public Alias getToTalkgroupAlias()
	{
		if( hasAliasList() )
		{
			return getAliasList().getMPT1327Alias( mToTalkgroup );
		}
		
		return null;
	}
	
	
	public int getChannelNumber()
	{
		return mChannelNumber;
	}
	/**
	 * Set the channel number.  This is used primarily for traffic channels since
	 * the channel will already have been identified prior to the traffic
	 * channel being created.
	 */
	public void setChannelNumber( int channel )
	{
		mChannelNumber = channel;
		
		broadcast( ChangedAttribute.CHANNEL_NUMBER );
	}
	
	@Override
    public String getActivitySummary()
    {
		StringBuilder sb = new StringBuilder();

		sb.append( "Activity Summary\n" );
		sb.append( "Decoder:\tMPT-1327\n" );
		sb.append( "Site:\t" );
		
		if( mSite != null )
		{
			sb.append( mSite );
			
			if( hasAliasList() )
			{
				Alias siteAlias = getAliasList().getSiteID( mSite );
				
				if( siteAlias != null )
				{
					sb.append( " " );
					sb.append( siteAlias.getName() );
					sb.append( "\n" );
				}
			}
		}
		else
		{
			sb.append( "Unknown\n" );
		}
		
		sb.append( "\n\n" );

		sb.append( "Talkgroups\n\n" );
		
		if( mGroups.isEmpty() )
		{
			sb.append( "  None\n" );
		}
		else
		{
			List<String> talkgroups = new ArrayList<String>( mGroups.keySet() );
			Collections.sort( talkgroups );

			for( String talkgroup: talkgroups )
			{
				sb.append( "  " );
				sb.append( talkgroup );
				
				if( hasAliasList() )
				{
					Alias alias = getAliasList().getMPT1327Alias( talkgroup );
					
					if( alias != null )
					{
						sb.append( " " );
						sb.append( alias.getName() );
					}
				}

				sb.append( "\n" );

				ArrayList<String> members = mGroups.get( talkgroup );
				Collections.sort( members );
				
				for( String member: members )
				{
					sb.append( "   >" );
					sb.append( member );

					if( hasAliasList() )
					{
						Alias alias = getAliasList().getMPT1327Alias( member );
						
						if( alias != null )
						{
							sb.append( " " );
							sb.append( alias.getName() );
						}
					}
					
					sb.append( "\n" );
				}
				
				sb.append( "\n" );
			}
		}

		sb.append( "\n" );
		
		sb.append( "Individual Idents\n" );
		if( mIdents.isEmpty() )
		{
			sb.append( "  None\n" );
		}
		else
		{
			Iterator<String> it = mIdents.iterator();
			
			while( it.hasNext() )
			{
				String ident = it.next();
				
				sb.append( "  " );
				sb.append( ident );
				sb.append( " " );
				
				if( hasAliasList() )
				{
					Alias alias = getAliasList().getMPT1327Alias( ident );
					
					if( alias != null )
					{
						sb.append( alias.getName() );
					}
				}
				
				sb.append( "\n" );
			}
		}
		
	    return sb.toString();
    }

	@Override
	public void init()
	{
		/* No initialization required */
	}

	@Override
	public void receiveDecoderStateEvent( DecoderStateEvent event )
	{
		switch( event.getEvent() )
		{
			case RESET:
				resetState();
				break;
			case SOURCE_FREQUENCY:
				mFrequency = event.getFrequency();
				break;
			case TRAFFIC_CHANNEL_ALLOCATION:
				if( event.getSource() != MPT1327DecoderState.this )
				{
					if( event instanceof TrafficChannelAllocationEvent )
					{
						TrafficChannelAllocationEvent allocationEvent = 
								(TrafficChannelAllocationEvent)event;

						String channel = allocationEvent.getCallEvent().getChannel();
						
						if( channel != null )
						{
							try
							{
								mChannelNumber = Integer.valueOf( channel );
								broadcast( ChangedAttribute.CHANNEL_NUMBER );
							}
							catch( Exception e )
							{
								//Do nothing, we couldn't parse the channel number
							}
						}
						
						mFrequency = allocationEvent.getCallEvent().getFrequency();
						broadcast( ChangedAttribute.SOURCE );

						mFromTalkgroup = allocationEvent.getCallEvent().getFromID();
						broadcast( ChangedAttribute.FROM_TALKGROUP );
						
						mToTalkgroup = allocationEvent.getCallEvent().getToID();
						broadcast( ChangedAttribute.TO_TALKGROUP );
					}
				}
				break;
			default:
				break;
		}
	}
}