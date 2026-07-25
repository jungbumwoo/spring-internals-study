/*
 * Copyright 2002-present the original author or authors.
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

package org.springframework.transaction.annotation;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;
import org.springframework.context.annotation.AutoProxyRegistrar;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.util.ClassUtils;

/**
 * Selects which implementation of {@link AbstractTransactionManagementConfiguration}
 * should be used based on the value of {@link EnableTransactionManagement#mode} on the
 * importing {@code @Configuration} class.
 *
 * @author Chris Beams
 * @author Juergen Hoeller
 * @since 3.1
 * @see EnableTransactionManagement
 * @see ProxyTransactionManagementConfiguration
 * @see TransactionManagementConfigUtils#TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME
 * @see TransactionManagementConfigUtils#JTA_TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME
 */
public class TransactionManagementConfigurationSelector extends AdviceModeImportSelector<EnableTransactionManagement> {

	/**
	 * Returns {@link ProxyTransactionManagementConfiguration} or
	 * {@code AspectJ(Jta)TransactionManagementConfiguration} for {@code PROXY}
	 * and {@code ASPECTJ} values of {@link EnableTransactionManagement#mode()},
	 * respectively.
	 */
	@Override
	protected String[] selectImports(AdviceMode adviceMode) {
		/*
		 * [@Transactional 프록시 흐름 1 - 인프라 등록]
		 * 기본값인 PROXY 모드에서는 두 종류의 인프라가 함께 등록된다.
		 *
		 * 1. AutoProxyRegistrar
		 *    BeanPostProcessor인 자동 프록시 생성기를 등록한다. 이 생성기는 각 빈을 만든 뒤
		 *    적용 가능한 Advisor가 있는지 검사하고, 있으면 원본 빈 대신 프록시를 노출한다.
		 * 2. ProxyTransactionManagementConfiguration
		 *    @Transactional을 찾는 pointcut과 실제 트랜잭션 경계를 만드는 advice를 등록한다.
		 *
		 * 따라서 @Transactional 자체가 객체를 프록시로 바꾸는 것이 아니다. 여기서 등록된
		 * 자동 프록시 생성기가 아래 설정의 Advisor를 발견할 때 프록시가 만들어진다.
		 */
		return switch (adviceMode) {
			case PROXY -> new String[] {AutoProxyRegistrar.class.getName(),
					ProxyTransactionManagementConfiguration.class.getName()};
			case ASPECTJ -> new String[] {determineTransactionAspectClass()};
		};
	}

	private String determineTransactionAspectClass() {
		return (ClassUtils.isPresent("jakarta.transaction.Transactional", getClass().getClassLoader()) ?
				TransactionManagementConfigUtils.JTA_TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME :
				TransactionManagementConfigUtils.TRANSACTION_ASPECT_CONFIGURATION_CLASS_NAME);
	}

}
