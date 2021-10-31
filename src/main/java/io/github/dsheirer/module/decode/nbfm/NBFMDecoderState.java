/*
 * *****************************************************************************
 *  Copyright (C) 2014-2020 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 * ****************************************************************************
 */
package io.github.dsheirer.module.decode.nbfm;

import io.github.dsheirer.audio.squelch.ISquelchStateListener;
import io.github.dsheirer.audio.squelch.SquelchState;
import io.github.dsheirer.audio.squelch.SquelchStateEvent;
import io.github.dsheirer.channel.state.DecoderState;
import io.github.dsheirer.channel.state.DecoderStateEvent;
import io.github.dsheirer.channel.state.DecoderStateEvent.Event;
import io.github.dsheirer.channel.state.State;
import io.github.dsheirer.identifier.Form;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierClass;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.identifier.string.SimpleStringIdentifier;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.sample.Listener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Narrow Band FM decoder channel state - provides the minimum channel state functionality
 */
public class NBFMDecoderState extends DecoderState implements ISquelchStateListener
{
    private final static Logger mLog = LoggerFactory.getLogger(NBFMDecoderState.class);
    private Identifier mChannelNameIdentifier;
    private String mChannelName;
    private Listener<SquelchStateEvent> mSquelchStateEventListener = new SquelchStateListener();

    public NBFMDecoderState(String channelName)
    {
        mChannelName = (channelName != null && !channelName.isEmpty()) ? channelName : "NBFM" + " CHANNEL";
        mChannelNameIdentifier = new SimpleStringIdentifier(mChannelName, IdentifierClass.USER, Form.CHANNEL_NAME, Role.TO);
    }

    @Override
    public void init()
    {
    }

    @Override
    public String getActivitySummary()
    {
        StringBuilder sb = new StringBuilder();

        sb.append("Activity Summary\n");
        sb.append("\tDecoder: NBFM");
        sb.append("\n\n");

        return sb.toString();
    }

    @Override
    public void receive(IMessage t)
    {
        /* Not implemented */
    }

    @Override
    public void receiveDecoderStateEvent(DecoderStateEvent event)
    {
        if(event.getEvent() == Event.REQUEST_RESET)
        {
            getIdentifierCollection().update(mChannelNameIdentifier);
        }
    }

    @Override
    public DecoderType getDecoderType()
    {
        return DecoderType.NBFM;
    }

    @Override
    public void start()
    {
        getIdentifierCollection().update(mChannelNameIdentifier);
    }

    @Override
    public void stop()
    {
    }

    @Override
    public Listener<SquelchStateEvent> getSquelchStateListener()
    {
        return mSquelchStateEventListener;
    }

    /**
     * Wrapper class to process squelch state events and convert them to decoder events for start/stop calls.
     */
    private class SquelchStateListener implements Listener<SquelchStateEvent>
    {
        @Override
        public void receive(SquelchStateEvent squelchStateEvent)
        {
            boolean squelched = squelchStateEvent.getSquelchState() == SquelchState.SQUELCH;

            mLog.debug("Received and Processing: " + squelchStateEvent);
            if(squelched)
            {
                broadcast(new DecoderStateEvent(this, Event.END, State.IDLE));
            }
            else
            {
                broadcast(new DecoderStateEvent(this, Event.START, State.CALL));
            }
        }
    }
}
