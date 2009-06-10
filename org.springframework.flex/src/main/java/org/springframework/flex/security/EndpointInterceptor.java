/*
 * Copyright 2002-2009 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.flex.security;

import org.springframework.flex.core.MessageInterceptor;
import org.springframework.flex.core.MessageProcessingContext;
import org.springframework.security.intercept.AbstractSecurityInterceptor;
import org.springframework.security.intercept.InterceptorStatusToken;
import org.springframework.security.intercept.ObjectDefinitionSource;

import flex.messaging.endpoints.AbstractEndpoint;
import flex.messaging.messages.CommandMessage;
import flex.messaging.messages.Message;

/**
 * Security interceptor that secures messages being passed to BlazeDS endpoints based on the security attributes
 * configured for the endpoint being invoked.
 * 
 * @author Jeremy Grelle
 */
@SuppressWarnings("unchecked")
public class EndpointInterceptor extends AbstractSecurityInterceptor implements MessageInterceptor {

    private static final String STATUS_TOKEN = "_enpointInterceptorStatusToken";

    private EndpointDefinitionSource objectDefinitionSource;

    public EndpointDefinitionSource getObjectDefinitionSource() {
        return this.objectDefinitionSource;
    }

    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public Class getSecureObjectClass() {
        return AbstractEndpoint.class;
    }

    /**
     * 
     * {@inheritDoc}
     */
    @Override
    public ObjectDefinitionSource obtainObjectDefinitionSource() {
        return this.objectDefinitionSource;
    }

    /**
     * 
     * {@inheritDoc}
     */
    public Message postProcess(MessageProcessingContext context, Message inputMessage, Message outputMessage) {
        if (context.getAttributes().containsKey(STATUS_TOKEN)) {
            InterceptorStatusToken token = (InterceptorStatusToken) context.getAttributes().get(STATUS_TOKEN);
            return (Message) afterInvocation(token, outputMessage);
        } else {
            return outputMessage;
        }
    }

    /**
     * 
     * {@inheritDoc}
     */
    public Message preProcess(MessageProcessingContext context, Message inputMessage) {
        if (!isPassThroughCommand(inputMessage)) {
            InterceptorStatusToken token = beforeInvocation(context.getMessageTarget());
            context.getAttributes().put(STATUS_TOKEN, token);
        }
        return inputMessage;
    }

    /**
     * Sets the {@link EndpointDefinitionSource} for the endpoint being secured
     * 
     * @param newSource the endpoint definition source
     */
    public void setObjectDefinitionSource(EndpointDefinitionSource newSource) {
        this.objectDefinitionSource = newSource;
    }

    private boolean isPassThroughCommand(Message message) {
        if (message instanceof CommandMessage) {
            CommandMessage command = (CommandMessage) message;
            return command.getOperation() == CommandMessage.CLIENT_PING_OPERATION || command.getOperation() == CommandMessage.LOGIN_OPERATION;
        }
        return false;
    }
}