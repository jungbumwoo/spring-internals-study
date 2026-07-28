Spring EventListener 내부 동작 파악을 위한 코드 리딩 가이드

Spring의 이벤트 시스템은 Observer(Publish-Subscribe) 패턴의 구현입니다. 전체 흐름은 다음과 같습니다:

publishEvent(event)
→ AbstractApplicationContext
→ ApplicationEventMulticaster.multicastEvent()
→ 매칭되는 ApplicationListener.onApplicationEvent() 호출

  ---
추천 리딩 순서

Phase 1: 핵심 인터페이스 (이벤트 시스템의 뼈대)

| 순서 | 파일                                   | 역할                                        |
  |------|----------------------------------------|---------------------------------------------|
| 1    | context/ApplicationEvent.java          | 모든 이벤트의 최상위 추상 클래스            |
| 2    | context/ApplicationListener.java       | 리스너 핵심 인터페이스 (onApplicationEvent) |
| 3    | context/ApplicationEventPublisher.java | 이벤트 발행 진입점 인터페이스               |
| 4    | context/PayloadApplicationEvent.java   | POJO를 이벤트로 감싸주는 래퍼               |

Phase 2: 이벤트 디스패치 엔진 (Multicaster)

| 순서 | 파일                                                   | 역할                                         |
  |------|--------------------------------------------------------|----------------------------------------------|
| 5    | context/event/ApplicationEventMulticaster.java         | 멀티캐스터 인터페이스 (리스너 등록/디스패치) |
| 6    | context/event/AbstractApplicationEventMulticaster.java | 리스너 레지스트리 + 캐시 구현                |
| 7    | context/event/SimpleApplicationEventMulticaster.java   | 유일한 구현체 — 동기/비동기 디스패치         |

Phase 3: @EventListener 어노테이션 지원

| 순서 | 파일                                                | 역할                                                  |
  |------|-----------------------------------------------------|-------------------------------------------------------|
| 8    | context/event/EventListener.java                    | @EventListener 어노테이션 정의                        |
| 9    | context/event/EventListenerMethodProcessor.java     | 빈 스캔 후 @EventListener 메서드를 찾아 리스너로 등록 |
| 10   | context/event/EventListenerFactory.java             | 메서드 → ApplicationListener 변환 전략 인터페이스     |
| 11   | context/event/DefaultEventListenerFactory.java      | 기본 팩토리 구현                                      |
| 12   | context/event/ApplicationListenerMethodAdapter.java | 핵심 — @EventListener 메서드를 감싸는 런타임 어댑터   |
| 13   | context/event/EventExpressionEvaluator.java         | SpEL condition 평가                                   |

Phase 4: 타입 필터링 계층

| 순서 | 파일                                                 | 역할                                            |
  |------|------------------------------------------------------|-------------------------------------------------|
| 14   | context/event/SmartApplicationListener.java          | Class 기반 이벤트/소스 타입 필터링              |
| 15   | context/event/GenericApplicationListener.java        | ResolvableType 기반 제네릭 타입 필터링          |
| 16   | context/event/GenericApplicationListenerAdapter.java | 일반 리스너를 GenericApplicationListener로 래핑 |

Phase 5: 라이프사이클 이벤트

| 순서 | 파일                                                              | 역할                         |
  |------|-------------------------------------------------------------------|------------------------------|
| 17   | context/event/ApplicationContextEvent.java                        | 컨텍스트 이벤트 추상 베이스  |
| 18   | context/event/ContextRefreshedEvent.java                          | 컨텍스트 초기화 완료 시 발행 |
| 19   | context/event/ContextClosedEvent.java                             | 컨텍스트 종료 시 발행        |
| 20   | context/event/ContextStartedEvent.java / ContextStoppedEvent.java | 명시적 시작/중지             |

Phase 6: 통합 지점 (모든 것이 연결되는 곳)

| 순서 | 파일                                            | 핵심 메서드                                                            |
  |------|-------------------------------------------------|------------------------------------------------------------------------|
| 21   | context/support/AbstractApplicationContext.java | initApplicationEventMulticaster(), registerListeners(), publishEvent() |

  ---
클래스 계층 요약

ApplicationEvent (abstract)
├── PayloadApplicationEvent<T>
└── ApplicationContextEvent (abstract)
├── ContextRefreshedEvent
├── ContextClosedEvent
├── ContextStartedEvent
└── ContextStoppedEvent

ApplicationListener<E> (interface)
└── SmartApplicationListener
└── GenericApplicationListener
├── ApplicationListenerMethodAdapter  ← @EventListener 메서드 래퍼
└── GenericApplicationListenerAdapter

ApplicationEventMulticaster (interface)
└── AbstractApplicationEventMulticaster
└── SimpleApplicationEventMulticaster  ← 유일한 구현체

---
핵심 포인트

1. SimpleApplicationEventMulticaster가 유일한 구현체 — 기본은 동기 실행, TaskExecutor 설정 시 비동기
2. EventListenerMethodProcessor — SmartInitializingSingleton으로 모든 싱글턴 생성 완료 후 @EventListener 메서드를 스캔
3. ApplicationListenerMethodAdapter — @EventListener 메서드의 실제 런타임 동작 (SpEL 조건 평가, 반환값 재발행 등)
4. AbstractApplicationContext.publishEvent() — 이벤트 발행의 실제 구현 + 부모 컨텍스트 전파

Phase 1 → 2 → 3 순서로 읽으면 전체 흐름이 잡히고, Phase 4~6은 세부 확장/응용입니다.
