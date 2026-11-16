/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.server.common.autoconfigure;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateResolver;
import org.springframework.beans.factory.support.GenericBeanDefinition;

/**
 * Keeps the MCP mapper out of ordinary injection while allowing explicit qualifiers. All
 * other candidate, value and lazy-resolution behavior is delegated unchanged.
 *
 * @author 芋道源码
 */
final class NonDefaultJsonMapperCandidateResolver implements AutowireCandidateResolver, BeanFactoryAware {

	private final AutowireCandidateResolver delegate;

	NonDefaultJsonMapperCandidateResolver(AutowireCandidateResolver delegate) {
		this.delegate = delegate;
	}

	@Override
	public boolean isAutowireCandidate(BeanDefinitionHolder holder, DependencyDescriptor descriptor) {
		BeanDefinition definition = holder.getBeanDefinition();
		if ("mcpServerJsonMapper".equals(holder.getBeanName()) && !definition.isAutowireCandidate()
				&& this.delegate.hasQualifier(descriptor)) {
			AbstractBeanDefinition qualified = definition instanceof AbstractBeanDefinition
					? ((AbstractBeanDefinition) definition).cloneBeanDefinition()
					: new GenericBeanDefinition(definition);
			qualified.setAutowireCandidate(true);
			return this.delegate.isAutowireCandidate(
					new BeanDefinitionHolder(qualified, holder.getBeanName(), holder.getAliases()), descriptor);
		}
		return this.delegate.isAutowireCandidate(holder, descriptor);
	}

	@Override
	public boolean isRequired(DependencyDescriptor descriptor) {
		return this.delegate.isRequired(descriptor);
	}

	@Override
	public boolean hasQualifier(DependencyDescriptor descriptor) {
		return this.delegate.hasQualifier(descriptor);
	}

	@Override
	public Object getSuggestedValue(DependencyDescriptor descriptor) {
		return this.delegate.getSuggestedValue(descriptor);
	}

	@Override
	public Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, String beanName) {
		return this.delegate.getLazyResolutionProxyIfNecessary(descriptor, beanName);
	}

	@Override
	public AutowireCandidateResolver cloneIfNecessary() {
		return new NonDefaultJsonMapperCandidateResolver(this.delegate.cloneIfNecessary());
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (this.delegate instanceof BeanFactoryAware) {
			((BeanFactoryAware) this.delegate).setBeanFactory(beanFactory);
		}
	}

}
