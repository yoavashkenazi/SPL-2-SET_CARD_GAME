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
    private PlayerInputQueue incomingActionsQueue;

    /**
     * playerTokens[i] = true iff the player has a token in slot i
     */
    protected final boolean[] playerTokens;//we need to examine if this is needed


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

        this.dealer = dealer;
        this.tokensLeft = env.config.featureSize;
        this.incomingActionsQueue = new PlayerInputQueue(env.config.featureSize);
        this.playerTokens = new boolean[env.config.tableSize]; //we need to examine if this is needed
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
            System.out.println("pulling from queue");
            Integer slot = incomingActionsQueue.take();
            System.out.println("After pulling from queue");
            if (this.playerTokens[slot]){
                this.removePlayerToken(slot);
            }
            else{
                synchronized(this.table){
                    System.out.println("player: "+ id + " slot: " + slot + " run");
                    this.placePlayerToken(slot);
                    // if the third token was placed, the newly formed set is sent to the dealer for checking.
                    if (tokensLeft==0){
                        dealer.addSetToCheck(this.getSet());
                        dealer.wakeDealerThread();
                    }
                }
                
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
                try { //we need to check what does this lines mean.
                    synchronized (this) {
                         wait();
                    }
                } catch (InterruptedException ignored) {}
            }
            env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        this.terminate=true;
        //we need to check for interruped
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        // TODO implement
        System.out.println("player: " + id + " slot: "+ slot + " keyPressed");
        this.incomingActionsQueue.put(slot);
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement
        try {
            this.playerThread.sleep(env.config.pointFreezeMillis);
        } catch (InterruptedException e) {}
        env.ui.setFreeze(this.id, env.config.pointFreezeMillis);

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {
        // TODO implement
        try {
            this.playerThread.sleep(env.config.penaltyFreezeMillis*3);
        } catch (InterruptedException e) {System.out.println("player inyerupted");}
        env.ui.setFreeze(this.id, env.config.penaltyFreezeMillis);
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
        for (int i =0; i < this.playerTokens.length ; i++){
            if (this.playerTokens[i]){
                setSlots [index] = i;
                setCards[index] = table.slotToCard[i];
                index++;
            }
        }
        return new PlayerSet(this.id, setSlots, setCards);
    }

    protected synchronized void removePlayerToken (int slot){
        if (playerTokens[slot]){
            this.playerTokens[slot] = false;
            table.removeToken(this.id, slot);
            this.tokensLeft++;
        }
    }

    protected synchronized void placePlayerToken (int slot){
        System.out.println("player: "+ id + " slot: " + slot + " placePlayerToken");
        if (!playerTokens[slot]){
            this.playerTokens[slot] = true;
            table.placeToken(this.id, slot);
            this.tokensLeft--;
        }
        
    }

    protected synchronized void removeAllPlayerTokens (){
        for (int i = 0 ; i < playerTokens.length ; i++){
            this.removePlayerToken(i);
        }
    }
}
