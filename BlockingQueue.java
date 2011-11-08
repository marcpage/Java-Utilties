import java.util.ArrayList;

/** A thread-safe queue with a maximum capacity that can block
	with a timeout on push (full queue) or pop (empty queue).
*/
public class BlockingQueue<E> {
	/** 
		@param max	The maximum number of elements in the queue at any one time
		@param initialCapacity	Preallocate space to be efficient
	*/
	public BlockingQueue(int max, int initialCapacity) {
		_queue= new ArrayList<E>(initialCapacity);
		_max= max;
	}
	/** 
		@param max	The maximum number of elements in the queue at any one time
	*/
	public BlockingQueue(int max) {
		_queue= new ArrayList<E>();
		_max= max;
	}
	/** 
		@return	true if the queue is empty
	*/
	public synchronized boolean empty() {
		return _queue.size() == 0;
	}
	/** 
		@return true if the queue has reached maximum capacity
	*/
	public synchronized boolean full() {
		return _queue.size() >= _max;
	}
	/**
		@param element	The element to push on the end of the queue
		@param timeoutInMilliseconds	The maximum time to wait for room in the queue 
											if the queue is full
		@return	true if we were able to push the element into the queue 
					before the timeout, false if the element did not go into the queue
	*/
	public synchronized boolean push(E element, long timeoutInMilliseconds) {
		long	finish= System.currentTimeMillis() + timeoutInMilliseconds;

		while(full()) {
			try	{
				long	now= System.currentTimeMillis();
				
				if(now >= finish) {
					if(!full()) {
						break;
					}
					return false;
				}
				wait(finish - now);
			} catch(InterruptedException e) {
			}
		}
		_queue.add(element);
		notify();
		return true;
	}
	/** 
		@param timeoutInMilliseconds	The maximum amount of time to wait if the queue is empty
											for an element to be pushed in
		@return			The element, or null if the queue is empty and the timeout expires
	*/
	public synchronized E pop(long timeoutInMilliseconds) {
		long	finish= System.currentTimeMillis() + timeoutInMilliseconds;
		E		element;
		
		while(empty()) {
			try	{
				long	now= System.currentTimeMillis();
				
				if(now >= finish) {
					if(!empty()) {
						break;
					}
					return null;
				}
				wait(finish - now);
			} catch(InterruptedException e) {
			}
		}
		element= _queue.remove(0);
		notify();
		return element;
	}
	/** The list of elements in the queue */
	private ArrayList<E>	_queue;
	/** The maxmimum number of elements the queue can have */
	private int				_max;
	/** Strictly for testing purposes.
	*/
	private static class _Test extends Thread {
		/**
			@param q		The queue to pound on
			@param offset	The offset of integers to push into the queue
			@param step		The increments from offset of the integers to push
			@param max		The limit value on the integers to push
		*/
		public _Test(BlockingQueue<String> q, int offset, int step, int max) {
			_q= q;
			_offset= offset;
			_step= step;
			_max= max;
			start();
		}
		/** Pushes integer strings onto the queue
		*/
		public void run() {
			for(int i= _offset; i < _max; i+= _step) {
				while(!_q.push(""+i, 100 /* block until we can put */)) {
					// keep trying
				}
			}
		}
		/** The queue to test */
		private BlockingQueue<String>	_q;
		private int						_offset, _step, _max;
	}
	/** Test. Should print out (not in order) all the numbers from zero to
			maxIntegerInQueue (exclusive).
	*/
	public static void main(String... args) {
		int						threads= 30;
		int						maxQueueSize= 8;
		int						maxIntegerInQueue= 1000;
		_Test[]					tests= new _Test[threads];
		BlockingQueue<String>	q= new BlockingQueue<String>(maxQueueSize);
		
		for(int i= 0; i < tests.length; ++i) {
			tests[i]= new _Test(q, i, tests.length, maxIntegerInQueue);
		}
		while(true) {
			String	s= q.pop(1000 /* milliseconds timeout if empty */);
			
			if(null == s) {
				break;
			}
			System.out.println(s);
		}
	}
}
