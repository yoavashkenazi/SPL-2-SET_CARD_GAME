package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;
import bguspl.set.Util;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;
    private final Player[] players;

    /**
     * The list of card ids that are left in the dealer's deck.
     */
    private final List<Integer> deck;

    /**
     * True iff game should be terminated.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime = Long.MAX_VALUE;

    /**
     * A queue that holds players sets that needs to be checked for legality, and does it fairly (FIFO)
     */
    private Queue<PlayerSet> setsToCheck;

    private Thread dealerThread;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.setsToCheck = new ConcurrentLinkedQueue<PlayerSet>();
        reshuffleTime = System.currentTimeMillis()+env.config.turnTimeoutMillis;
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        this.dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        for (Player p : players){
            ThreadLogger playerThread = new ThreadLogger(p, "player "+ p.id, env.logger);
            playerThread.startWithLog();
        }
        while (!shouldFinish()) {
            placeCardsOnTable();
            updateTimerDisplay(true);
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
        }
        //terminating all threads gracefully and in reverse order to the order they were created in.
        for (int i = (players.length-1) ; i >= 0 ; i--) {
            players[i].terminate();
            System.out.println("player " + i + " have been terminated");
        }
        announceWinners();
        env.logger.info("thread " + Thread.currentThread().getName() + " terminated.");
    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did not time out.
     */
    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();
            placeCardsOnTable();
        }
    }

    /**
     * Called when the game should be terminated.
     */
    public void terminate() {
        // TODO implement
        this.terminate = true;
    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    private void removeCardsFromTable() {
        // TODO implement
        // gets the set to be checked
        //System.out.println("removeCardsFromTable");
        PlayerSet setToCheck = this.setsToCheck.poll();
        if (setToCheck != null){
            int[] slotSet = setToCheck.getSetSlots();
            int[] cardsSet = setToCheck.getSetCards();
            // checking the validity of the set (if the cards that were chosen by the player are the same cards that are currently on the table.)
            boolean isValidSet = true;
            for (int i = 0 ; i < slotSet.length ; i++){
                if (cardsSet[i] != this.table.slotToCard[slotSet[i]]){
                    isValidSet = false;
                }
            }
            //if the set is valid
            if (isValidSet){
                //if the set is a legal set
                if (env.util.testSet(cardsSet)){
                    players[setToCheck.getPlayerId()].point();
                    //removes the cards and tokens from the set`s slots.
                    this.table.beforeWrite();
                    for (int slot : slotSet){
                        //for each player, tries to remove his token from slot slot. 
                        for (Player p : players) {
                            p.waitingForDealerCheck = false;
                            p.removePlayerToken(slot);
                        }
                        this.table.removeCard(slot);
                    }
                    this.table.afterWrite();
                    //if a set was found, update the timer
                    this.updateTimerDisplay(true);
                }
                else{
                    //if the player selected an incorrect set, gives him a penalty and removes his tokens.
                    System.out.println("before penalty is called on player: " + setToCheck.getPlayerId());
                    players[setToCheck.getPlayerId()].penalty();
                    players[setToCheck.getPlayerId()].waitingForDealerCheck = false;
                }
            }
        }
    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        //run on each slot and if the slot is empty and there is a card in the deck, place the card from the deck to the slot.
        for (int i = 0 ; i < env.config.tableSize ; i++) {
            if (table.slotToCard[i]==-1 && !deck.isEmpty()){
                this.table.beforeWrite();
                table.placeCard(deck.remove(0), i);
                this.table.afterWrite();
                //every card, update the freeze timer of the players.
                for (Player player : players) {
                    env.ui.setFreeze(player.id, player.timeToFreeze-System.currentTimeMillis());
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try {
            this.dealerThread.sleep(10);
        } catch (InterruptedException e) {System.out.println("dealer interupted");}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        //System.out.println("updateTimer");
        // TODO implement
        //if the timer needs to be reseted
        if (reset){
            reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
        }
        long timeleft = reshuffleTime-System.currentTimeMillis();
        //ensuring the timer wwont display negative numbers.
        if (timeleft<0){
            timeleft = 0;
        }
        //updating the timer
        env.ui.setCountdown(timeleft, reshuffleTime-System.currentTimeMillis()<env.config.turnTimeoutWarningMillis);
        //updating the freezing time of each player.
        for (Player player : players) {
            env.ui.setFreeze(player.id, player.timeToFreeze-System.currentTimeMillis());
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        //remove all of the tokens of the players from the table
        this.table.beforeWrite();
        for (Player p : players) {
            p.waitingForDealerCheck=false;
            p.removeAllPlayerTokens();
        }
        
        int i = 0;
        //for each card, removes the card from the table and adds it to the deck
        for (Integer cardId : table.slotToCard) {
            if (cardId!=-1){
                table.removeCard(i);
                deck.add(cardId);
                //every card, update the players freeze timer
                for (Player player : players) {
                    env.ui.setFreeze(player.id, player.timeToFreeze-System.currentTimeMillis());
                }
            }
            i++;
        }
        this.table.afterWrite();
        // after the cards have been collected, shuffle the deck
        Collections.shuffle(deck);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        int maxScore = 0;
        //the updating list of players with the maximum score
        List<Integer> winnersList = new LinkedList<Integer>();
        //runs on each player and checks if his score is the highest 
        for (int i = 0 ; i < env.config.players ; i++){
            if (players[i].score()>maxScore){
                maxScore = players[i].score();
                winnersList = new LinkedList<Integer>();
                winnersList.add(i);
            }
            else if(players[i].score()==maxScore){
                winnersList.add(i);
            }
        }
        //announces the winners.
        env.ui.announceWinner((winnersList.stream().mapToInt(Integer::intValue)).toArray());
        // try {
        //     Thread.sleep(5000);
        // } catch (InterruptedException e) {}
    }

    public void addSetToCheck (PlayerSet setToCheck){
        setsToCheck.add(setToCheck);
    }

    protected void wakeDealerThread (){
        if (this.dealerThread != null){
            System.out.println("wakeDealerThread method");
            this.dealerThread.interrupt();
        }
    }
}
