/** Multithreaded Event Queue messaging node.
*/
public class MessageQueue {
	public class Event {
		public Event(int type, String key, byte[] value, MessageQueue response) {
			set(type, key, value, response);
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
		public MessageQueue getResults() {
			return _response;
		}
		public void set(int type, String key, byte[] value, MessageQueue response) {
			_type= type;
			_key= key;
			_value= value;
			_response= response;
		}
		private int 			_type;
		private String			_key;
		private byte[]			_value;
		private MessageQueue	_response;
	}
	public MessageQueue(int maxPendingEvents) {
		_queue= new BlockingQueue<Event>(maxPendingEvents);
		_recycle= new BlockingQueue<Event>(1000 /* max recycled nodes */);
	}
	public boolean send(int type, String key, byte[] value, MessageQueue response) {
		Event	toSend= null;

		if(!_recycle.empty()) {
			toSend= _recycle.pop(1 /* milliseconds to wait for a recycled event */ );
		}
		if(null == toSend) {
			toSend= new Event(type, key, value, response);
		} else {
			toSend.set(type, key, value, response);
		}
		return _queue.push(toSend, 10 /* milliseconds to wait if the queue is full */ );
	}
	public Event next(long timeoutInMilliseconds) {
		return _queue.pop(timeoutInMilliseconds);
	}
	public void recycleEvent(Event done) {
		_recycle.push(done, 1 /* milliseconds to wait if we've reached max recycled events */ );
	}
	private BlockingQueue<Event>	_recycle;
	private BlockingQueue<Event>	_queue;
}
