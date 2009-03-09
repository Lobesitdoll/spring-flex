package org.springframework.flex.messaging.config.xml;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.beans.factory.support.ManagedSet;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.flex.messaging.config.FlexConfigurationManager;
import org.springframework.flex.messaging.security.SecurityExceptionTranslator;
import org.springframework.security.ConfigAttributeDefinition;
import org.springframework.security.SpringSecurityException;
import org.springframework.security.intercept.web.RequestKey;
import org.springframework.security.util.AntUrlPathMatcher;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

/**
 * Configures a Spring-managed MessageBroker
 * 
 * @author Jeremy Grelle
 *
 */
public class MessageBrokerBeanDefinitionParser extends
		AbstractSingleBeanDefinitionParser {

	// --------------------------- Full qualified class names ----------------//
	private static final String MESSAGE_BROKER_FACTORY_BEAN_CLASS_NAME = "org.springframework.flex.messaging.MessageBrokerFactoryBean";
	private static final String MESSAGE_BROKER_HANDLER_ADAPTER_CLASS_NAME = "org.springframework.flex.messaging.servlet.MessageBrokerHandlerAdapter";
	private static final String DEFAULT_HANDLER_MAPPING_CLASS_NAME = "org.springframework.web.servlet.handler.SimpleUrlHandlerMapping";
	private static final String LOGIN_COMMAND_CLASS_NAME = "org.springframework.flex.messaging.security.SpringSecurityLoginCommand";
	private static final String SECURITY_PROCESSOR_CLASS_NAME = "org.springframework.flex.messaging.security.MessageBrokerSecurityConfigProcessor";
	private static final String EXCEPTION_TRANSLATION_CLASS_NAME = "org.springframework.flex.messaging.security.ExceptionTranslationAdvice";
	private static final String ENDPOINT_INTERCEPTOR_CLASS_NAME = "org.springframework.flex.messaging.security.EndpointInterceptor";
	private static final String SERVICE_MESSAGE_ADVISOR_CLASS_NAME = "org.springframework.flex.messaging.security.EndpointServiceMessagePointcutAdvisor";
	private static final String ENDPOINT_DEFINITION_SOURCE_CLASS_NAME = "org.springframework.flex.messaging.security.EndpointDefinitionSource";
	private static final String REMOTING_PROCESSOR_CLASS_NAME = "org.springframework.flex.messaging.remoting.RemotingServiceConfigProcessor";
		
	// --------------------------- XML Config Attributes ---------------------//
	private static final String CONFIGURATION_MANAGER_ATTR = "configuration-manager";
	private static final String SERVICES_CONFIG_PATH_ATTR = "services-config-path";
	private static final String DISABLE_DEFAULT_MAPPING_ATTR = "disable-default-mapping";
	private static final String PATTERN_ATTR = "pattern";
	private static final String REF_ATTR = "ref";
	private static final String AUTH_MANAGER_ATTR = "authentication-manager";
	private static final String ACCESS_MANAGER_ATTR = "access-decision-manager";
	private static final String PER_CLIENT_AUTHENTICATION_ATTR = "per-client-authentication";
	private static final String INVALIDATE_FLEX_SESSION_ATTR = "invalidate-flex-session";
	private static final String ACCESS_ATTR = "access";
	private static final String CHANNEL_ATTR = "channel";

	// --------------------------- Bean Configuration Properties -------------//
	private static final String URL_MAP_PROPERTY = "urlMap";
	private static final String CONFIG_PROCESSORS_PROPERTY = "configProcessors";
	private static final String PER_CLIENT_AUTHENTICATION_PROPERTY = "perClientAuthentication";
	private static final String INVALIDATE_FLEX_SESSION_PROPERTY = "invalidateFlexSession";
	private static final String AUTH_MANAGER_PROPERTY = "authenticationManager";
	private static final String ACCESS_MANAGER_PROPERTY = "accessDecisionManager";
	private static final String OBJECT_DEF_SOURCE_PROPERTY = "objectDefinitionSource";
	private static final String EXCEPTION_TRANSLATORS_PROPERTY = "exceptionTranslators";

	// --------------------------- XML Child Elements ------------------------//
	private static final String MAPPING_PATTERN_ELEMENT = "mapping";
	private static final String CONFIG_PROCESSOR_ELEMENT = "config-processor";
	private static final String SECURED_ELEMENT = "secured";
	private static final String SECURED_CHANNEL_ELEMENT = "secured-channel";
	private static final String SECURED_ENDPOINT_PATH_ELEMENT = "secured-endpoint-path";
	private static final String REMOTING_SERVICE_ELEMENT = "remoting-service";
	
	// --------------------------- Default Values ----------------------------//
	private static final Object DEFAULT_MAPPING_PATH = "/*";
	
	@Override
	protected String getBeanClassName(Element element) {
		return MESSAGE_BROKER_FACTORY_BEAN_CLASS_NAME;
	}

	@Override
	protected void doParse(Element element, ParserContext parserContext,
			BeanDefinitionBuilder builder) {

		CompositeComponentDefinition componentDefinition = new CompositeComponentDefinition(
				element.getLocalName(), parserContext.extractSource(element));
		parserContext.pushContainingComponent(componentDefinition);
		
		//Initialize the configProcessors set
		ManagedSet configProcessors = new ManagedSet();
		configProcessors.setSource(element);
		
		// Set the default ID if necessary
		if (!StringUtils.hasText(element.getAttribute(ID_ATTRIBUTE))) {
			element.setAttribute(ID_ATTRIBUTE, BeanIds.MESSAGE_BROKER);
		}

		validateMessageBroker(element, parserContext);

		ParsingUtils.mapOptionalAttributes(element, builder, SERVICES_CONFIG_PATH_ATTR);
		
		ParsingUtils.mapOptionalBeanRefAttributes(element, builder, CONFIGURATION_MANAGER_ATTR);

		registerHandlerAdapterIfNecessary(element, parserContext);
		
		if(!Boolean.parseBoolean(element.getAttribute(DISABLE_DEFAULT_MAPPING_ATTR))) {
			registerHandlerMappings(element, parserContext, DomUtils.getChildElementsByTagName(element, MAPPING_PATTERN_ELEMENT));
		}
		
		registerCustomConfigProcessors(parserContext, configProcessors, DomUtils.getChildElementsByTagName(element, CONFIG_PROCESSOR_ELEMENT));
		
		configureRemotingService(element, parserContext, configProcessors, DomUtils.getChildElementByTagName(element, REMOTING_SERVICE_ELEMENT));
		
		configureSecurity(element, parserContext, configProcessors, DomUtils.getChildElementByTagName(element, SECURED_ELEMENT));
		
		if (!configProcessors.isEmpty()) {
			builder.addPropertyValue(CONFIG_PROCESSORS_PROPERTY, configProcessors);
		}
		
		parserContext.popAndRegisterContainingComponent();
	}
	
	@SuppressWarnings("unchecked")
	private void configureRemotingService(Element parent,
			ParserContext parserContext, ManagedSet configProcessors,
			Element remotingServiceElement) {
		Element source = remotingServiceElement != null ? remotingServiceElement : parent; 
		BeanDefinitionBuilder remotingProcessorBuilder = BeanDefinitionBuilder.genericBeanDefinition(REMOTING_PROCESSOR_CLASS_NAME);
		
		if (remotingServiceElement != null) {
			ParsingUtils.mapAllAttributes(remotingServiceElement, remotingProcessorBuilder);
		}
		
		String brokerId = parent.getAttribute(ID_ATTRIBUTE);
		
		registerInfrastructureComponent(source, parserContext, remotingProcessorBuilder, brokerId+BeanIds.REMOTING_PROCESSOR_SUFFIX);
		configProcessors.add(new RuntimeBeanReference(brokerId+BeanIds.REMOTING_PROCESSOR_SUFFIX));
	}

	@SuppressWarnings("unchecked")
	private void configureSecurity(Element parent, ParserContext parserContext, ManagedSet configProcessors, Element securedElement) {
		
		if (securedElement == null) {
			return;
		}
		
		boolean perClientAuthentication = Boolean.parseBoolean(securedElement.getAttribute(PER_CLIENT_AUTHENTICATION_ATTR));

		String authManager = securedElement.getAttribute(AUTH_MANAGER_ATTR);
		if (!StringUtils.hasText(authManager)) {
			authManager = org.springframework.security.config.BeanIds.AUTHENTICATION_MANAGER;
		}
		
		String accessManager = securedElement.getAttribute(ACCESS_MANAGER_ATTR);
		if (!StringUtils.hasText(accessManager)) {
			accessManager = org.springframework.security.config.BeanIds.ACCESS_MANAGER;
		}
		
		String brokerId = parent.getAttribute(ID_ATTRIBUTE);
		registerLoginCommand(brokerId, parserContext, configProcessors, securedElement, authManager, perClientAuthentication);
		
		BeanDefinitionBuilder securityProcessorBuilder = BeanDefinitionBuilder.genericBeanDefinition(SECURITY_PROCESSOR_CLASS_NAME);
		ManagedList advisors = new ManagedList();
		
		registerExceptionTranslation(securedElement, parserContext, advisors);
		registerEndpointInterceptorIfNecessary(securedElement, parserContext, advisors, authManager, accessManager);
		
		securityProcessorBuilder.addConstructorArgValue(advisors);
		registerInfrastructureComponent(securedElement, parserContext, securityProcessorBuilder, brokerId+BeanIds.SECURITY_PROCESSOR_SUFFIX);
		configProcessors.add(new RuntimeBeanReference(brokerId+BeanIds.SECURITY_PROCESSOR_SUFFIX));
	}

	@SuppressWarnings("unchecked")
	private void registerCustomConfigProcessors(ParserContext parserContext, Set configProcessors, List configProcessorElements) {
		if (!CollectionUtils.isEmpty(configProcessorElements)) {
			Iterator i = configProcessorElements.iterator();
			while(i.hasNext()) {
				Element configProcessorElement = (Element) i.next();
				configProcessors.add(new RuntimeBeanReference(configProcessorElement.getAttribute(REF_ATTR)));
			}	
		}
	}
	
	@SuppressWarnings("unchecked")
	private void registerEndpointInterceptorIfNecessary(Element securedElement,
			ParserContext parserContext, ManagedList advisors, String authManager, String accessManager) {
		if (securedElement.hasChildNodes()) {
			BeanDefinitionBuilder advisorBuilder = BeanDefinitionBuilder.genericBeanDefinition(SERVICE_MESSAGE_ADVISOR_CLASS_NAME);
			
			BeanDefinitionBuilder interceptorBuilder = BeanDefinitionBuilder.genericBeanDefinition(ENDPOINT_INTERCEPTOR_CLASS_NAME);
			interceptorBuilder.addPropertyReference(AUTH_MANAGER_PROPERTY, authManager);
			interceptorBuilder.addPropertyReference(ACCESS_MANAGER_PROPERTY, accessManager);
			
			BeanDefinitionBuilder endpointDefSourceBuilder = BeanDefinitionBuilder.genericBeanDefinition(ENDPOINT_DEFINITION_SOURCE_CLASS_NAME);
			
			HashMap endpointMap = new HashMap();
			List securedChannelElements = DomUtils.getChildElementsByTagName(securedElement, SECURED_CHANNEL_ELEMENT);
			if (!CollectionUtils.isEmpty(securedChannelElements)) {
				Iterator i = securedChannelElements.iterator();
				while(i.hasNext()) {
					Element securedChannel = (Element) i.next();
					String access = securedChannel.getAttribute(ACCESS_ATTR);
					String channel = securedChannel.getAttribute(CHANNEL_ATTR);
					Object attributeDefinition = parseConfigAttributeDefinition(access);
					endpointMap.put(channel, attributeDefinition);
				}
			}
			
			LinkedHashMap requestMap = new LinkedHashMap();
			List securedEndpointPathElements = DomUtils.getChildElementsByTagName(securedElement, SECURED_ENDPOINT_PATH_ELEMENT);
			if (!CollectionUtils.isEmpty(securedEndpointPathElements)) {
				Iterator i = securedEndpointPathElements.iterator();
				while (i.hasNext()) {
					Element securedPath = (Element) i.next();
					String access = securedPath.getAttribute(ACCESS_ATTR);
					RequestKey pattern = new RequestKey(securedPath.getAttribute(PATTERN_ATTR));
					Object attributeDefinition = parseConfigAttributeDefinition(access);
					requestMap.put(pattern, attributeDefinition);
				}
			}
			
			endpointDefSourceBuilder.addConstructorArgValue(new AntUrlPathMatcher());
			endpointDefSourceBuilder.addConstructorArgValue(requestMap);
			endpointDefSourceBuilder.addConstructorArgValue(endpointMap);
			
			String endpointDefSourceId = registerInfrastructureComponent(securedElement, parserContext, endpointDefSourceBuilder);
			interceptorBuilder.addPropertyReference(OBJECT_DEF_SOURCE_PROPERTY, endpointDefSourceId);
			String interceptorId = registerInfrastructureComponent(securedElement, parserContext, interceptorBuilder);
			advisorBuilder.addConstructorArgReference(interceptorId);
			String advisorId = registerInfrastructureComponent(securedElement, parserContext, advisorBuilder);
			advisors.add(new RuntimeBeanReference(advisorId));
		}
	}

	private Object parseConfigAttributeDefinition(String access) {
		if (StringUtils.hasText(access)) {
            return new ConfigAttributeDefinition(StringUtils.commaDelimitedListToStringArray(access));
        } else {
            return null;
        }
	}

	@SuppressWarnings("unchecked")
	private void registerExceptionTranslation(Element securedElement,
			ParserContext parserContext,
			ManagedList advisors) {
		BeanDefinitionBuilder advisorBuilder = BeanDefinitionBuilder.genericBeanDefinition(SERVICE_MESSAGE_ADVISOR_CLASS_NAME);
		BeanDefinitionBuilder exceptionTranslationBuilder = BeanDefinitionBuilder.genericBeanDefinition(EXCEPTION_TRANSLATION_CLASS_NAME);
		ManagedMap translators = new ManagedMap();
		translators.put(SpringSecurityException.class, new SecurityExceptionTranslator());
		exceptionTranslationBuilder.addPropertyValue(EXCEPTION_TRANSLATORS_PROPERTY, translators);
		String exceptionTranslationId = registerInfrastructureComponent(securedElement, parserContext, exceptionTranslationBuilder);
		advisorBuilder.addConstructorArgReference(exceptionTranslationId);
		String advisorId = registerInfrastructureComponent(securedElement, parserContext, advisorBuilder);
		advisors.add(new RuntimeBeanReference(advisorId));
	}
	
	private void registerHandlerAdapterIfNecessary(Element element,
			ParserContext parserContext) {
		//Make sure we only ever register one MessageBrokerHandlerAdapter 
		if (!parserContext.getRegistry().containsBeanDefinition(BeanIds.MESSAGE_BROKER_HANDLER_ADAPTER))
		{
			BeanDefinitionBuilder handlerAdapterBuilder = BeanDefinitionBuilder
					.genericBeanDefinition(MESSAGE_BROKER_HANDLER_ADAPTER_CLASS_NAME);
			
			registerInfrastructureComponent(element, parserContext, handlerAdapterBuilder, BeanIds.MESSAGE_BROKER_HANDLER_ADAPTER);
		}
	}
	
	@SuppressWarnings("unchecked")
	private void registerHandlerMappings(Element parent, ParserContext parserContext, List mappingPatternElements) {
		BeanDefinitionBuilder handlerMappingBuilder = BeanDefinitionBuilder
				.genericBeanDefinition(DEFAULT_HANDLER_MAPPING_CLASS_NAME);
		
		Map mappings = new HashMap();
		if (CollectionUtils.isEmpty(mappingPatternElements)){
			mappings.put(DEFAULT_MAPPING_PATH, parent.getAttribute(ID_ATTRIBUTE));
		} else {
			Iterator i = mappingPatternElements.iterator();
			while(i.hasNext()) {
				Element mappingElement = (Element) i.next();
				mappings.put(mappingElement.getAttribute(PATTERN_ATTR), parent.getAttribute(ID_ATTRIBUTE));
			}
		}
			
		handlerMappingBuilder.addPropertyValue(URL_MAP_PROPERTY, mappings);
		registerInfrastructureComponent(parent, parserContext, handlerMappingBuilder, parent.getAttribute(ID_ATTRIBUTE)+BeanIds.HANDLER_MAPPING_SUFFIX);
	}
	
	@SuppressWarnings("unchecked")
	private void registerLoginCommand(String brokerId,
			ParserContext parserContext, ManagedSet configProcessors,
			Element securedElement, String authManager, boolean perClientAuthentication) {
		
		String loginCommandId = brokerId+BeanIds.LOGIN_COMMAND_SUFFIX;
		boolean invalidateFlexSession = Boolean.parseBoolean(securedElement.getAttribute(INVALIDATE_FLEX_SESSION_ATTR));
		
		BeanDefinitionBuilder loginCommandBuilder = BeanDefinitionBuilder
			.genericBeanDefinition(LOGIN_COMMAND_CLASS_NAME);
		loginCommandBuilder.addConstructorArgReference(authManager);
		loginCommandBuilder.addPropertyValue(PER_CLIENT_AUTHENTICATION_PROPERTY, perClientAuthentication);
		loginCommandBuilder.addPropertyValue(INVALIDATE_FLEX_SESSION_PROPERTY, invalidateFlexSession);
		
		registerInfrastructureComponent(securedElement, parserContext, loginCommandBuilder, loginCommandId);
		configProcessors.add(new RuntimeBeanReference(loginCommandId));
	}

	private void validateMessageBroker(Element element,
			ParserContext parserContext) {
		
		if (!FlexConfigurationManager.DEFAULT_CONFIG_PATH.equals(element.getAttribute(SERVICES_CONFIG_PATH_ATTR))
				&& StringUtils.hasText(element
						.getAttribute(CONFIGURATION_MANAGER_ATTR))) {
			parserContext
					.getReaderContext()
					.error(
							"The "
									+ SERVICES_CONFIG_PATH_ATTR
									+ " cannot be set when using a custom "
									+ CONFIGURATION_MANAGER_ATTR
									+ " reference.  Set the configurationPath on the custom ConfigurationManager instead.",
							element);

		}
	}
	
	private String registerInfrastructureComponent(Element element,
			ParserContext parserContext, BeanDefinitionBuilder componentBuilder) {
		String beanName = parserContext.getReaderContext().generateBeanName(
				componentBuilder.getRawBeanDefinition());
		registerInfrastructureComponent(element, parserContext, componentBuilder, beanName);
		return beanName;
	}

	private void registerInfrastructureComponent(Element element,
			ParserContext parserContext, BeanDefinitionBuilder componentBuilder, String beanName) {
		componentBuilder.getRawBeanDefinition().setSource(
				parserContext.extractSource(element));
		componentBuilder.getRawBeanDefinition().setRole(
				BeanDefinition.ROLE_INFRASTRUCTURE);
		parserContext.registerBeanComponent(new BeanComponentDefinition(
				componentBuilder.getBeanDefinition(), beanName));
	}

}