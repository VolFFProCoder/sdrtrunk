/*
 * *****************************************************************************
 * Copyright (C) 2014-2023 Dennis Sheirer
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

package io.github.dsheirer.module.decode.ip.ars.identifier;

import io.github.dsheirer.identifier.Form;
import io.github.dsheirer.identifier.IdentifierClass;
import io.github.dsheirer.identifier.Role;
import io.github.dsheirer.identifier.string.StringIdentifier;
import io.github.dsheirer.protocol.Protocol;

/**
 * Automatic Registration Service - Device Identifier
 */
public class ARSDevice extends StringIdentifier
{
    /**
     * Constructs an instance
     * @param value of the device identifier
     * @param role of the device (TO/FROM)
     */
    public ARSDevice(String value, Role role)
    {
        super(value, IdentifierClass.USER, Form.ARS_DEVICE, role);
    }

    @Override
    public Protocol getProtocol()
    {
        return Protocol.ARS;
    }

    /**
     * Creates an ARS device with a FROM role
     */
    public static ARSDevice createFrom(String value)
    {
        return new ARSDevice(value, Role.FROM);
    }

    /**
     * Creates an ARS device with a TO role
     */
    public static ARSDevice createTo(String value)
    {
        return new ARSDevice(value, Role.TO);
    }
}
