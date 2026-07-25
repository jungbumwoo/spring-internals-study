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

import java.util.Collection;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportAware;
import org.springframework.context.annotation.Role;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.config.TransactionManagementConfigUtils;
import org.springframework.transaction.event.TransactionalEventListenerFactory;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.util.CollectionUtils;

/**
 * Abstract base {@code @Configuration} class providing common structure for enabling
 * Spring's annotation-driven transaction management capability.
 *
 * @author Chris Beams
 * @author Stephane Nicoll
 * @author Juergen Hoeller
 * @since 3.1
 * @see EnableTransactionManagement
 */
@Configuration
public abstract class AbstractTransactionManagementConfiguration implements ImportAware {

	protected @Nullable AnnotationAttributes enableTx;

	/**
	 * Default transaction manager, as configured through a {@link TransactionManagementConfigurer}.
	 */
	protected @Nullable TransactionManager txManager;


	@Override
	public void setImportMetadata(AnnotationMetadata importMetadata) {
		this.enableTx = AnnotationAttributes.fromMap(
				importMetadata.getAnnotationAttributes(EnableTransactionManagement.class.getName()));
		if (this.enableTx == null) {
			throw new IllegalArgumentException(
					"@EnableTransactionManagement is not present on importing class " + importMetadata.getClassName());
		}
	}

	@Autowired(required = false)
	void setConfigurers(Collection<TransactionManagementConfigurer> configurers) {
		if (CollectionUtils.isEmpty(configurers)) {
			return;
		}
		if (configurers.size() > 1) {
			throw new IllegalStateException("Only one TransactionManagementConfigurer may exist");
		}
		TransactionManagementConfigurer configurer = configurers.iterator().next();
		this.txManager = configurer.annotationDrivenTransactionManager();
	}


	@Bean
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public TransactionAttributeSource transactionAttributeSource() {
		/*
		 * [@Transactional 프록시 흐름 2 - 애노테이션을 메타데이터로 변환]
		 * AnnotationTransactionAttributeSource는 메서드/클래스의 @Transactional을 찾아
		 * propagation, isolation, timeout, readOnly, rollback 규칙 등이 담긴
		 * TransactionAttribute로 변환하고 캐시한다.
		 *
		 * 같은 객체는 프록시 생성 시 "이 빈에 트랜잭션 메서드가 있는가?"를 판단하는 pointcut과,
		 * 호출 시 "이번 메서드에 어떤 트랜잭션 규칙을 적용할 것인가?"를 판단하는 interceptor가
		 * 함께 사용한다.
		 */
		// Accept protected @Transactional methods on CGLIB proxies, as of 6.0
		AnnotationTransactionAttributeSource tas = new AnnotationTransactionAttributeSource(false);
		// Apply default rollback rule, as of 6.2
		if (this.enableTx != null && this.enableTx.getEnum("rollbackOn") == RollbackOn.ALL_EXCEPTIONS) {
			tas.addDefaultRollbackRule(RollbackRuleAttribute.ROLLBACK_ON_ALL_EXCEPTIONS);
		}
		return tas;
	}

	@Bean(name = TransactionManagementConfigUtils.TRANSACTIONAL_EVENT_LISTENER_FACTORY_BEAN_NAME)
	@Role(BeanDefinition.ROLE_INFRASTRUCTURE)
	public static TransactionalEventListenerFactory transactionalEventListenerFactory() {
		return new RestrictedTransactionalEventListenerFactory();
	}

}
