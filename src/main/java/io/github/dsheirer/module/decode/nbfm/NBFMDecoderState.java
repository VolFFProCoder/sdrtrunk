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

import io.github.dsheirer.channel.state.DecoderState;
import io.github.dsheirer.channel.state.DecoderStateEvent;
import io.github.dsheirer.channel.state.DecoderStateEvent.Event;
import io.github.dsheirer.identifier.Form;
import io.github.dsheirer.identifier.Identifier;
import io.github.dsheirer.identifier.IdentifierClass;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.identifier.string.SimpleStringIdentifier;
import io.github.dsheirer.message.IMessage;
import io.github.dsheirer.module.decode.DecoderType;
import io.github.dsheirer.module.decode.event.DecodeEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Narrow Band FM decoder channel state - provides the minimum channel state functionality
 */
public class NBFMDecoderState extends DecoderState
{
    private final static Logger mLog = LoggerFactory.getLogger(NBFMDecoderState.class);
    private Identifier mChannelNameIdentifier;
    private String mChannelName;
    private DecodeEvent mDecodeEvent;

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
        switch(event.getEvent())
        {
            case REQUEST_RESET ->
                    {
                        getIdentifierCollection().update(mChannelNameIdentifier);
                    }
            case START ->
                    {
                        createDecodeEvent();
                        broadcast(mDecodeEvent);
                    }
            case END ->
                    {
                        if(mDecodeEvent == null)
                        {
                            createDecodeEvent();
                        }

                        mDecodeEvent.end(System.currentTimeMillis());
                        broadcast(mDecodeEvent);
                        mDecodeEvent = null;
                    }
            case CONTINUATION ->
                    {
                        if(mDecodeEvent == null)
                        {
                            createDecodeEvent();
                        }

                        mDecodeEvent.update(System.currentTimeMillis());
                        broadcast(mDecodeEvent);
                    }
        }
    }

    private void createDecodeEvent()
    {
        mDecodeEvent = DecodeEvent.builder(System.currentTimeMillis())
                .eventDescription("CALL")
                .details("NBFM")
                .timeslot(0)
                .identifiers(getIdentifierCollection())
                .build();
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
}
