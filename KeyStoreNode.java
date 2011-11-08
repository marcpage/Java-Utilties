/** Base class for a key-store node that used in a messaging, highly threaded environment. 
*/
public class KeyStoreNode {
	public class Event {
		public Event(int type, String key, byte[] value, KeyStoreNode results) {
			set(type, key, value, results);
		}
		public int getType() {
			return _type;
		}
		public String getKey() {
			return _key;
		}
		public byte[] getValue() {
			return _value;
		}
		public KeyStoreNode getResults() {
			return _results;
		}
		public void set(int type, String key, byte[] value, KeyStoreNode results) {
			_type= type;
			_key= key;
			_value= value;
			_results= results;
		}
		private int 			_type;
		private String			_key;
		private byte[]			_value;
		private KeyStoreNode	_results;
	}
	public KeyStoreNode(int maxPendingEvents) {
		_queue= new BlockingQueue<Event>(maxPendingEvents);
		_recycle= new BlockingQueue<Event>(1000 /* max recycled nodes */);
	}
	public boolean send(int type, String key, byte[] value, KeyStoreNode results) {
		Event	toSend= null;
		
		if(!_recycle.empty()) {
			toSend= _recycle.pop(1 /* milliseconds to wait for a recycled event */ );
		}
		if(null == toSend) {
			toSend= new Event(type, key, value, results);
		} else {
			toSend.set(type, key, value, results);
		}
		return _queue.push(toSend, 10 /* milliseconds to wait if the queue is full */ );
	}
	protected Event _next(long timeoutInMilliseconds) {
		return _queue.pop(timeoutInMilliseconds);
	}
	protected void _recycleEvent(Event done) {
		_recycle.push(done, 1 /* milliseconds to wait if we've reached max recycled events */ );
	}
	private BlockingQueue<Event>	_recycle;
	private BlockingQueue<Event>	_queue;
}
