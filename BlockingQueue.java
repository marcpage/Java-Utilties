import java.util.ArrayList;

public class BlockingQueue<E> {
	public BlockingQueue(int initialCapacity, int max) {
		_queue= new ArrayList<E>(initialCapacity);
		_max= max;
	}
	public BlockingQueue(int max) {
		_queue= new ArrayList<E>();
		_max= max;
	}
	public synchronized boolean empty() {
		return _queue.size() == 0;
	}
	public synchronized boolean full() {
		return _queue.size() >= _max;
	}
	/**
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
	private ArrayList<E>	_queue;
	private int				_max;
	private static class _Test extends Thread {
		public _Test(BlockingQueue<String> q, int offset, int step, int max) {
			_q= q;
			_offset= offset;
			_step= step;
			_max= max;
			start();
		}
		public void run() {
			for(int i= _offset; i < _max; i+= _step) {
				while(!_q.push(""+i, 100 /* block until we can put */)) {
					// keep trying
				}
			}
		}
		private BlockingQueue<String>	_q;
		private int						_offset, _step, _max;
	}
	public static void main(String... args) {
		_Test[]					tests= new _Test[30];
		BlockingQueue<String>	q= new BlockingQueue<String>(8);
		
		for(int i= 0; i < tests.length; ++i) {
			tests[i]= new _Test(q, i, tests.length, 1000);
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
