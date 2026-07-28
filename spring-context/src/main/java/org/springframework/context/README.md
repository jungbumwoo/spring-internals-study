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


---
eventListener를 사용하는 비용:
이벤트 객체 생성, ResolvableType 계산, 리스너 캐시 조회, 리플렉션(@EventListener인 경우) 어댑터 호출됨

그럼 왜 써야함?
- 발행자와 구독자가 서로 알아서는 안 되는 경우 (모듈 경계, 순환 의존 회피)
- 하나의 이벤트에 여러 구독자가 붙거나, 앞으로 붙을 가능성이 실질적으로 있는 경우


리플렉션(@EventListener인 경우) 어댑터 호출 되는게 왜 비용인가요?
method.invoke() 자체의 리플렉션 오버헤드는 JVM이 웜업된 후에는 사실 크지 않습니다. 정확히 어디서 비용이 발 생하는지 코드 기준으로 짚어보겠습니다.

method.invoke() 자체는 생각보다 안 느립니다

JVM 리플렉션은 두 단계로 동작합니다:
1. 처음 15~16회 호출까지는 NativeMethodAccessorImpl을 통해 JNI로 호출 (느림)
2. 그 이후 JIT이 GeneratedMethodAccessor라는 바이트코드를 즉석에서 생성해서 일반 메서드 호출과 거의 동등한 속도로 동작 (sun.reflect.inflation 최적화)

즉 로그를 자주 남기는 이벤트라면 웜업 후엔 이 부분 자체는 큰 비용이 아닙니다. 여기서 제가 앞서 "리플렉션이라서 느리다"고 한 건 과장이었습니다.

실제 비용은 매 호출마다 반복되는 다른 작업들입니다

ApplicationListenerMethodAdapter.processEvent() (ApplicationListenerMethodAdapter.java:249-260)를 한 번 호출할 때마다 실제로 벌어지는 일:

1. resolveArguments(event) (:292-309) — getResolvableType(event) 호출로 이벤트의 제네릭 타입을 매번 재계산하고, PayloadApplicationEvent인지 instanceof 체크, 페이로드 언래핑 여부 판단.
2. shouldHandle() (:272-284) — condition 속성이 설정돼 있다면 매 호출마다 SpEL 표현식을 평가합니다 (EventExpressionEvaluator). 이건 리플렉션보다 훨씬 무겁습니다 — SpEL 파싱/캐싱된 Expression이라도 StandardEvaluationContext 구성, 빈 리졸버 조회 등이 들어갑니다. condition을 안 쓰면 이 비용은 없습니다.
3. doInvoke() (:363-390) — getTargetBean()으로 매번 빈 팩토리에서 대상 빈을 조회, ReflectionUtils.makeAccessible(this.method) 호출(캐시된 accessible 플래그 체크지만 매번 호출), InvocationTargetException 언래핑을 위한 try-catch 오버헤드.
4. Object[] 배열 박싱 — 인자를 담기 위한 배열 할당.

이 중에서 실질적으로 눈에 띄는 비용은 condition SpEL 평가입니다. 나머지(리플렉션 호출, 배열 할당, instanceof 체크)는 마이크로초 단위라 로그 한 줄 남기는 수준의 작업에서는 사실상 무시해도 되는 수준입니다.


> JIT이 바이트코드를 변환하기 전에, NativeMethodAccessorImpl을 통해 JNI로 호출하는것은 왜 느린가요?

⏺ 니다. "JNI로 호출"이라고 표현했는데, 정확히 왜 그게 느린지 단계별로 설명하겠습니다.

Method.invoke()의 초기 경로

setAccessible(true) 이후 method.invoke(bean, args)를 호출하면 내부적으로 MethodAccessor 구현체에 위임합니다. 처음 15회(기본 sun.reflect.inflationThreshold=15)까지는 NativeMethodAccessorImpl을 사용합니다.

느린 이유

1. 네이티브 경계 전환(JNI transition) 비용

NativeMethodAccessorImpl.invoke0()는 native 메서드로 선언되어 있어서, JVM 인터프리터/JIT 코드에서 HotSpot의 C++ 구현(JVM_InvokeMethod 계열)으로 넘어갑니다. 이 전환에는:
- 스레드 상태를 _thread_in_Java → _thread_in_native로 전환 (GC가 이 스레드의 스택을 스캔할 수 있도록 상태 기록)
- 세이프포인트(safepoint) 체크와의 동기화 — GC가 진행 중이면 네이티브 진입/복귀 시점에 대기해야 할 수 있음
- 콜 프레임 세팅

이 자체가 순수 Java-to-Java 메서드 호출보다 고정 오버헤드를 추가합니다. I/O 없이도 "경계를 넘는다"는 것 자체가 공짜가 아닙니다.

2. 매 호출마다 인자 검사/언박싱

Method.invoke(Object, Object[])로 넘어온 args 배열은 네이티브 코드 쪽에서 각 인자에 대해:
- 선언된 파라미터 타입과 실제 전달값의 타입 호환성 검사 (widening conversion 처리 등)
- 박싱된 Object를 실제 호출 규약(calling convention)에 맞는 값으로 언박싱

이 과정을 매 호출마다 반복합니다. 일반 메서드 호출은 컴파일 타임에 타입이 고정되어 있어 이런 런타임 검사가 필요 없는데, 리플렉션 호출은 타입이 런타임에 결정되므로 매번 다시 확인합니다.

3. JIT이 최적화할 수 없음

JIT 컴파일러는 인터프리터/컴파일된 Java 코드는 인라이닝, escape analysis, 분기 예측 등 다양한 최적화를 적용할 수 있지만, native 메서드 호출은 JIT에게 불투명한 블랙박스입니다. 그 안에서 무슨 일이 일어나는지 JIT이 들여다보고 최적화할 수 없어서, 이 코드가 아무리 자주(hot) 호출돼도 매번 동일한 고정 비용을 그대로 지불합니다.

4. 예외 래핑

대상 메서드가 던진 예외를 잡아서 InvocationTargetException으로 감싸는 처리도 네이티브 레이어에서 매번 수행됩니다.

Inflation이 이 문제를 해결하는 방식

15~16회 호출 후 JVM은 sun.reflect.MethodAccessorGenerator를 통해 실제 바이트코드를 그 자리에서 생성합니다(GeneratedMethodAccessorNN). 이 생성된 클래스는 타겟 메서드를 직접 호 출하는 일반 Java 코드이므로:
- JNI 경계 전환이 없음
- JIT이 일반 Java 메서드처럼 인라이닝/최적화 가능
- 타입 체크가 코드 생성 시점에 고정되어 런타임 재검사가 줄어듦