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
        protected volatile boolean terminate;
  
        public PlayerInputQueue(int max) {
                vec_ = new Vector<Integer>(); 
                MAX = max; } 
  
        public synchronized int size(){ 
                return vec_.size(); 
        } 
  
        public synchronized void put(Integer e){ 
                //System.out.println("before put");
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
        
        public synchronized void terminate(){
                this.terminate=true;
                notifyAll();
        }
        
// Implementations of additional methods... 
}
