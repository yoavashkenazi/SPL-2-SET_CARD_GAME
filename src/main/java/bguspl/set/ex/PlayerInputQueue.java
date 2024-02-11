package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit; 
  
class PlayerInputQueue{ 
  
        // a vector, used to implement the queue 
        private Vector<Integer> vec_;
        private final int MAX; 
  
        public PlayerInputQueue(int max) { MAX = max; } 
  
        public synchronized int size(){ 
                return vec_.size(); 
        } 
  
        public synchronized void put(Integer e){ 
                while(size()>=MAX){ 
                        try{ 
                                this.wait(); 
                        } catch (InterruptedException ignored){} 
                } 
  
                vec_.add(e); 
                // wakeup everybody. If someone is waiting in the get() 
                // method, it can now perform the get. 
                this.notifyAll(); 
        } 
  
        public synchronized Integer take(){ 
                while(size()==0){ 
                        try{ 
                                this.wait(); 
                        } catch (InterruptedException ignored){} 
                } 
  
                Integer e = vec_.get(0);
                vec_.remove(0); 
                // wakeup everybody. If someone is waiting in the add()  
                // method, it can now perform the add. 
                this.notifyAll(); 
                return e; 
        }
        
// Implementations of additional methods... 
}
