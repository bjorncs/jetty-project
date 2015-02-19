//
//  ========================================================================
//  Copyright (c) 1995-2015 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.http2.server;

import org.eclipse.jetty.server.HttpChannelState;
import org.eclipse.jetty.server.HttpInput;


// TODO This class is the same as the default.  Is it needed?
public class HttpInputOverHTTP2 extends HttpInput
{
    private final HttpChannelState _httpChannelState;
    
    public HttpInputOverHTTP2(HttpChannelState httpChannelState)
    {
        _httpChannelState=httpChannelState;
    }
    
    @Override
    protected void onReadPossible()
    {
        _httpChannelState.onReadPossible();
    }
}
