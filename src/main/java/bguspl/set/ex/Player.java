package bguspl.set.ex;

import java.util.LinkedList;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.BlockingQueue;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The current score of the player.
     */
    private int score;

    //our code
    /**
     * tokensLeft = the number of tokens player playerId is not using right now.
     */
    protected int tokensLeft; 

    /**
     * blocking queue that holds incoming actions. the queue is of capacity 3
     */
    protected PlayerInputQueue incomingActionsQueue;

    /**
     * playerTokens[i] = true iff the player has a token in slot i
     */
    //protected final boolean[] playerTokens;//we need to examine if this is needed

    //private volatile long currentFreezeTime;
    
    protected long timeToFreeze;

    protected volatile boolean waitingForDealerCheck;


    private Dealer dealer;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.id = id;
        this.human = human;

        //this.currentFreezeTime = 0;
        this.dealer = dealer;
        this.tokensLeft = env.config.featureSize;
        this.incomingActionsQueue = new PlayerInputQueue(env.config.featureSize);
        //this.playerTokens = new boolean[env.config.tableSize]; //we need to examine if this is needed
    }

    /**
     * The main player thread of each player starts here (main loop for the player thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        if (!human) createArtificialIntelligence();
        while (!terminate) {
            // TODO implement main player loop
            //taking an action from the actions queue.
            Integer slot = incomingActionsQueue.take();
            //checking if the player is in freeze time
            // long freezeTimeDelta = this.timeToFreeze-System.currentTimeMillis();
            // if (freezeTimeDelta>0){
            //     // if he is frozen, sleep for as much time that is needed 
            //     try {
            //         Thread.sleep(freezeTimeDelta);
            //     } catch (InterruptedException e) {}
            //     this.currentFreezeTime = 0;
            // }
            
            //if the game is done, exit the loop.
            if (terminate){break;}
            //if there is a token on the slot -> remove the token
            if (this.table.playersTokens[this.id][slot]){
                this.removePlayerToken(slot);
            }

            else if (tokensLeft>0){
                this.table.beforeRead();
                this.placePlayerToken(slot);
                // if the third token was placed, the newly formed set is sent to the dealer for checking.
                if (tokensLeft==0 && !this.waitingForDealerCheck){
                    this.waitingForDealerCheck = true;
                    System.out.println("changing the flag to true");
                    dealer.addSetToCheck(this.getSet());
                    dealer.wakeDealerThread();
                }
                this.table.afterRead();    
            }
        }
        if (!human) try { aiThread.join(); } catch (InterruptedException ignored) {}
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it is not full.
     */
    private void createArtificialIntelligence() {
        // note: this is a very, very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
            while (!terminate) {
                // TODO implement player key press simulator
                Random random = new Random();
                this.keyPressed(random.nextInt(env.config.tableSize)); // Generates a random integer between 0 (inclusive) and tableSize (exclusive)
                // try { //we need to check what does this lines means.
                //     synchronized (this) {
                //          wait();
                //     }
                // } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
        System.out.println("aithread finished: " + this.id);
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        this.terminate=true;
        this.incomingActionsQueue.put(0);
        //we need to check for interruped
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        //System.out.println("player: " + id + " slot: "+ slot + " keyPressed");
        //only if the player is not frozen, the action is added to the queue.
        if (!this.waitingForDealerCheck && timeToFreeze-System.currentTimeMillis()<0){
            this.incomingActionsQueue.put(slot);
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        //this.currentFreezeTime = env.config.pointFreezeMillis;
        env.ui.setFreeze(this.id, env.config.pointFreezeMillis);
        this.timeToFreeze = System.currentTimeMillis() + env.config.pointFreezeMillis;

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        System.out.println("penalty");
        // TODO implement
        //currentFreezeTime = env.config.penaltyFreezeMillis;
        env.ui.setFreeze(this.id, env.config.penaltyFreezeMillis);
        this.timeToFreeze = System.currentTimeMillis() + env.config.penaltyFreezeMillis;
    }

    public int score() {
        return score;
    }

    /**
     * return the PlayerSet of the Player
     */
    private PlayerSet getSet(){
        int []setSlots = new int [env.config.featureSize];
        int []setCards = new int [env.config.featureSize];
        int index=0;
        // for (int i =0; i < this.playerTokens.length ; i++){
        //     if (this.playerTokens[i]){
        //         setSlots [index] = i;
        //         setCards[index] = table.slotToCard[i];
        //         index++;
        //     }
        // }

        for (int i =0; i < this.table.playersTokens[this.id].length ; i++){
            if (this.table.playersTokens[this.id][i]){
                setSlots [index] = i;
                setCards[index] = table.slotToCard[i];
                index++;
            }
        }
        return new PlayerSet(this.id, setSlots, setCards);
    }

    protected synchronized void removePlayerToken (int slot){
        // if (playerTokens[slot]){
        //     this.playerTokens[slot] = false;
        //     table.removeToken(this.id, slot);
        //     this.tokensLeft++;
        // }
        
        if (!this.waitingForDealerCheck && this.table.removeToken(this.id, slot)){
            this.tokensLeft++;
        }
    }

    protected synchronized void placePlayerToken (int slot){
        // if (!playerTokens[slot] && this.table.slotToCard[slot]!=-1){
        //     this.playerTokens[slot] = true;
        //     table.placeToken(this.id, slot);
        //     this.tokensLeft--;
        // }
        
        if (this.tokensLeft > 0 && !this.waitingForDealerCheck && !this.table.playersTokens[this.id][slot] && this.table.slotToCard[slot]!=-1){
            this.table.placeToken(this.id, slot);
            this.tokensLeft--;
        }
    }

    /**
     * removes all of the tokens the player have on the table.
     */
    protected synchronized void removeAllPlayerTokens (){
        for (int i = 0 ; i < this.table.playersTokens[this.id].length ; i++){
            this.removePlayerToken(i);
        }
    }
}
