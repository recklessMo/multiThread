## java多线程系列之future机制

#### future是什么？

* 在执行比较耗时的任务的时候，我们经常会采取新开线程执行的方式，比如在netty中，如果在io线程中处理耗cpu的计算任务，那么就会造成io线程的堵塞，导致吞吐率的下降（比较好理解，本来这个时候io线程可以去处理io的，现在确在等待cpu执行计算任务），这严重影响了io的效率。
* 一般我们采用线程池来执行异步任务，一般情况下不需要获取返回值，但是特殊情况下是需要获取返回值的，也就是需要拿到异步任务的执行结果，举个例子来说：对大量整数进行求和，如果采用多线程来求解，就需要一个汇总线程和多个计算线程，计算线程执行具体的计算任务并且返回求和值，汇总线程进行多个求和值最后的汇总。
* 那么如果我们自己要实现这个异步计算的程序的话可以采用什么方式呢？这实际上是线程之间的通信机制，即我们的汇总线程需要拿到所有计算线程执行完毕的结果，那么我们可以采用共享内存来实现，定义一个全局的map，每个计算线程执行完毕的结果都放到到map中，然后汇总线程从全局map中取出结果进行累加汇总，这样就搞定了，这里面虽然思想很简单，但是还是有一些细节需要考虑的，比如汇总线程怎么判断所有的任务都执行完毕呢？可以通过计算任务的总数和已经完成计算任务的数目进行比较。总之我们肯定可以实现一套这样的异步计算框架。
* 那么进一步抽象，在上面的实现过程中，实际上我们关心的就是每个任务执行的结果，以及任务是否执行完毕，对应到上面提到的计算框架，就是我们关心是否计算完毕和计算完毕后的值，有了这两部分的值，我们的汇总线程就能够很方便的进行计算总的结果了。
* 其实仔细观察，对于几乎所有的异步执行线程，我们都是关心这两部分值的，即任务是否执行完毕，执行完后的结果（如果不需要结果可以返回null），那么这两部分的东西肯定可以抽象出来，避免我们每次编写线程执行的run方法的时候都要自己提交结果和设置完成标志，于是java就是设计了这么一套future机制来帮助开发者

上面就是我结合自己的理解分析的future机制的设计思想，可能说的不够全，希望有人可以补充。下面会讲解java future的具体实现

####总结一句话：我们需要异步执行任务并且知道异步任务的执行结果和执行状态，我们可以自己来实现，但是由于这部分比较通用，所以java通过一种future机制来为我们实现了这些功能，这就是future。



###下面分析java里面future机制的具体实现

* execute方式：我们知道一个类如果实现了runnable接口，它就能够被线程来执行，因为实现了runnable接口就拥有了run方法，所以能够被执行。所以最简单的异步线程执行方式如下：利用Executors框架来创建一个线程池，然后调用execute方法来提交异步任务，注意这里的execute方法是没有返回的，也就是说我们没法知道提交的任务的执行结果。

  ```java
  ExecutorService executorService = Executors.newSingleThreadExecutor();
  executorService.execute(()->System.out.println("异步执行!"));
  ```


* submit方式：前面提到的java给我们提供的线程池接口ExecutorService提供了两种提交异步任务的方式，一种就是没有返回值的execute方法（由于ExecutorService接口是extends了Executor接口的，所以拥有了execute方法），还有一种是带有返回值的submit方法。在submit方法中，提供了三个重载方法：

  ```java
  <T> Future<T> submit(Callable<T> task);
      Future<?> submit(Runnable task);
  <T> Future<T> submit(Runnable task, T result);
  ```

  可以看到，submit方法支持实现了callable和runnable的task，不同于runnable只有没有返回值的run方法，callable提供了一个带返回值的call方法，可以有返回值。正是因为runnable没有返回值，所以第二个重载方法返回值为null，第三个重载方法里面可以从外部设置一个返回值，这个返回值将会作为runnable的返回值。具体代码如下：

  ```java
  	public <T> Future<T> submit(Callable<T> task) {
          if (task == null) throw new NullPointerException();
          RunnableFuture<T> ftask = newTaskFor(task);
          execute(ftask);
          return ftask;
      }
      public Future<?> submit(Runnable task) {
          if (task == null) throw new NullPointerException();
          RunnableFuture<Void> ftask = newTaskFor(task, null);
          execute(ftask);
          return ftask;
      }
      public <T> Future<T> submit(Runnable task, T result) {
          if (task == null) throw new NullPointerException();
          RunnableFuture<T> ftask = newTaskFor(task, result);
          execute(ftask);
          return ftask;
      }
  ```

  两个方法都调用newTaskFor方法来创建了一个RunnableFuture的对象，然后调用execute方法来执行这个对象，说明我们线程池真正执行的对象就是这个RunnableFuture对象。

  ```java
      protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
          return new FutureTask<T>(runnable, value);
      }
      protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
          return new FutureTask<T>(callable);
      }
  ```

  由上面代码看出就是创建了一个futureTask对象，这个对象封装了我们提供的runnable和callable对象。futuretask实现了runnablefuture接口，这就是说明futuretask具备了runnable的功能（能被线程执行）和future功能（能够获取自身执行的结果和状态）。能被线程执行功能是我们自己通过实现runnable接口或者callable接口来完成的。future功能前面我们提过是很通用的功能，所以java给我们实现了。下面就进入futuretask查看。

* futuretask对象：futuretask是真正的future功能实现的地方。前面说过这个一个RunnableFuture对象，所以我们看看它的run方法

  ```java
      private volatile int state;
      private static final int NEW          = 0;
      private static final int COMPLETING   = 1;
      private static final int NORMAL       = 2;
      private static final int EXCEPTIONAL  = 3;
      private static final int CANCELLED    = 4;
      private static final int INTERRUPTING = 5;
      private static final int INTERRUPTED  = 6;	
  	/** 封装的callable对象 */
      private Callable<V> callable;
      /** task的执行结果 */
      private Object outcome; 
      /** 当前线程池的哪个线程正在执行这个task */
      private volatile Thread runner;
      /** 等待的线程列表 */
      private volatile WaitNode waiters;

  	public void run() {
          if (state != NEW ||
              !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                           null, Thread.currentThread()))
              return;
          try {
              Callable<V> c = callable;// 1. 内部包装的一个callable对象
              if (c != null && state == NEW) {
                  V result;
                  boolean ran;
                  try {
                      result = c.call();// 2. 调用包装的call方法
                      ran = true;
                  } catch (Throwable ex) {
                      result = null;
                      ran = false;
                      setException(ex);
                  }
                  if (ran)
                      set(result);//3. 设置返回值
              }
          } finally {
              // runner must be non-null until state is settled to
              // prevent concurrent calls to run()
              runner = null;
              // state must be re-read after nulling runner to prevent
              // leaked interrupts
              int s = state;
              if (s >= INTERRUPTING)
                  handlePossibleCancellationInterrupt(s);
          }
      }
  ```

  前面提到futuretask是封装了runnable和callable的，可是为什么内部只有一个callable呢，实际上是因为futuretask自己调用适配器转换了一下：代码如下，采用了java的适配器模式。

  ```java
      public FutureTask(Runnable runnable, V result) {
          this.callable = Executors.callable(runnable, result);
          this.state = NEW;       // ensure visibility of callable
      }
      
      public static <T> Callable<T> callable(Runnable task, T result) {
          if (task == null)
              throw new NullPointerException();
          return new RunnableAdapter<T>(task, result);
      }

      static final class RunnableAdapter<T> implements Callable<T> {
          final Runnable task;
          final T result;
          RunnableAdapter(Runnable task, T result) {
              this.task = task;
              this.result = result;
          }
          public T call() {
              task.run();
              return result;
          }
      }
  ```

  futuretask的run方法调用了内部封装的callable对象的call方法，获取返回值，并且设置到自己outcome中，state代表执行的状态，这样就通过代理的方式代理了我们的callable的call方法，帮助我们获取执行的结果和状态，所以我们自己编写业务逻辑的时候就不用去管这层通用的逻辑了。这里面还有一个waitnode我们单独讲

*    WaitNode: 通过前面的分析我们知道，实际上我们submit任务之后返回的future对象就是线程池为我们创建的runnablefuture对象，也就是futuretask这个对象。future接口为我们提供了一系列的方法，如下

     ```java
         V get() throws InterruptedException, ExecutionException;
         boolean cancel(boolean mayInterruptIfRunning);
     ```

     上面是主要的两个方法，get和cancel，cancel的时候调用runner的interrupt方法即可

     ```java
         public boolean cancel(boolean mayInterruptIfRunning) {
             if (!(state == NEW &&
                   UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                       mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
                 return false;
             try {    // in case call to interrupt throws exception
                 if (mayInterruptIfRunning) {
                     try {
                         Thread t = runner;
                         if (t != null)
                             t.interrupt();
                     } finally { // final state
                         UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                     }
                 }
             } finally {
                 finishCompletion();
             }
             return true;
         }
     ```

     其中unsafe是用于cas操作的，在java并发包中大量用到，后续会讲解。

     get方法的设计是阻塞的，也就是说如果结果没有返回时需要等待的，所以才会有waitnode这个对象的产生，当多个线程都调用futuretask的get方法的时候，如果结果还没产生，就都需要等待，这时候所有等待的线程就会形成一个链表，所以waitnode实际上就是线程的链表。

     ```java
         static final class WaitNode {
             volatile Thread thread;
             volatile WaitNode next;
             WaitNode() { thread = Thread.currentThread(); }
         }
     ```

     再看get方法：如果任务没有完成就调用awaitDone进入阻塞，如果完成了直接调用report返回结果

     ```java
         public V get() throws InterruptedException, ExecutionException {
             int s = state;
             if (s <= COMPLETING)
                 s = awaitDone(false, 0L);
             return report(s);
         }
     ```

     ```java
         private int awaitDone(boolean timed, long nanos)
             throws InterruptedException {
             final long deadline = timed ? System.nanoTime() + nanos : 0L;
             WaitNode q = null;
             boolean queued = false;
             for (;;) {
                 if (Thread.interrupted()) {//1. 如果等待过程中，被中断过了，那么就移除自己
                     removeWaiter(q);
                     throw new InterruptedException();
                 }

                 int s = state;
                 if (s > COMPLETING) {
                     if (q != null)
                         q.thread = null;
                     return s;
                 }
                 else if (s == COMPLETING) // cannot time out yet
                     Thread.yield();
                 else if (q == null)
                     q = new WaitNode();
                 else if (!queued)//2. cas更新链表节点
                     queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                          q.next = waiters, q);
                 else if (timed) {
                     nanos = deadline - System.nanoTime();
                     if (nanos <= 0L) {
                         removeWaiter(q);
                         return state;
                     }
                     LockSupport.parkNanos(this, nanos);//3. locksupport原语让线程进入休眠
                 }
                 else
                     LockSupport.park(this);
             }
         }
     ```

     还是比较好看懂，其中LockSupport是原语，让线程进行休眠。如果线程在休眠中醒来了，有可能是多种情况，比如get的时间到了，也就是从3中醒来了，这样的话下一次循环就会判断时间到了，从而remove掉节点退出。还有可能等待的线程被interrupt了，这时候就会走到1的逻辑，通过判断中断标记将其remove掉。

     既然有了waitnode这个等待链表，那么肯定会有相应的唤醒机制，当执行完毕之后就会将waitnode链表上的线程一次唤醒，如下。

     ```java
         private void finishCompletion() {
             // assert state > COMPLETING;
             for (WaitNode q; (q = waiters) != null;) {
                 if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                     for (;;) {
                         Thread t = q.thread;
                         if (t != null) {
                             q.thread = null;
                             LockSupport.unpark(t);
                         }
                         WaitNode next = q.next;
                         if (next == null)
                             break;
                         q.next = null; // unlink to help gc
                         q = next;
                     }
                     break;
                 }
             }

             done();

             callable = null;        // to reduce footprint
         }
     ```

     ​



### 实际上java的future接口所提供的功能比较有限，比如listen机制就没有，都需要异步任务发起者主动去查询状态和结果，而且没有提供非阻塞的等待机制。但是我们可以自己灵活的实现，后续将参照netty中的future机制进行详细讲解。