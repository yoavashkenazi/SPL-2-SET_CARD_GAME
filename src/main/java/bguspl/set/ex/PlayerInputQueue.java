package bguspl.set.ex;
import java.util.Vector;

  
class PlayerInputQueue{ 
        //Blocking queue implementation
        // a vector, used to implement the queue 
        private Vector<Integer> vec_;
        private final int MAX; 
        protected volatile boolean terminate;
  
        public PlayerInputQueue(int max) {
                vec_ = new Vector<Integer>(); 
                MAX = max; } 
  
        public synchronized int size(){ 
                return vec_.size(); 
        } 
  
        public synchronized void put(Integer e){ 
                while(size()>=MAX && !this.terminate ){ 
                        try{ 
                                this.wait(); 
                        } catch (InterruptedException ignored){} 
                }
                if (!terminate){
                        vec_.add(e);
                } 
                this.notifyAll(); 
        } 
  
        public synchronized Integer take(){ 
                while(size()==0 && !this.terminate){ 
                        try{ 
                                this.wait(); 
                        } catch (InterruptedException ignored){} 
                } 
                if (terminate){
                        return -1;
                }
                Integer e = vec_.get(0);
                vec_.remove(0); 
                // wakeup everybody. If someone is waiting in the add()  
                // method, it can now perform the add. 
                this.notifyAll(); 
                return e; 
        }
        
        /**
         * The method is called when the game is terminated, in order to terminate all threads gracfully.
         */
        public synchronized void terminate(){
                this.terminate=true;
                notifyAll();
        }
}
