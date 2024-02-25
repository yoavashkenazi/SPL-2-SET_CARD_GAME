package bguspl.set.ex;

import bguspl.set.Env;
import bguspl.set.ThreadLogger;
import bguspl.set.Util;

import java.util.ArrayList;
import java.util.Arrays;
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

    /**
     * Saves the time of the last action (reshuffle of set collected)
     */
    private long lastActionTime = 0;

    private Thread dealerThread;

    public Dealer(Env env, Table table, Player[] players) {
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        this.setsToCheck = new ConcurrentLinkedQueue<PlayerSet>();
        //if the game mode is regular, set the first reshuffle time.
        if (env.config.turnTimeoutMillis>0){
            reshuffleTime = System.currentTimeMillis()+env.config.turnTimeoutMillis;
        }
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        this.dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        //creating and starting the players threads.
        for (Player p : players){
            ThreadLogger playerThread = new ThreadLogger(p, "player "+ p.id, env.logger);
            playerThread.startWithLog();
        }
        //shuffling the deck for the first time
        Collections.shuffle(deck);
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
        // gets the set to be checked
        PlayerSet setToCheck = this.setsToCheck.poll();
        if (setToCheck != null){
            int[] slotSet = setToCheck.getSetSlots();
            int[] cardsSet = setToCheck.getSetCards();
            // checking the validity of the set (if the cards that were chosen by the player are the same cards that are currently on the table.)
            boolean isValidSet = true;
            for (int i = 0 ; i < slotSet.length ; i++){
                if (slotSet[i]<0 || cardsSet[i] != this.table.slotToCard[slotSet[i]]){
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
        //creating a random order of cards placing.
        List<Integer> slotsList = new LinkedList<Integer>();
        for (int i = 0 ; i < env.config.tableSize ; i++){
            slotsList.add(i);
        }
        Collections.shuffle(slotsList);
        //indicates if the table has been changed in this turn. (for hints update)
        boolean tableHasBeenChanged = false;
        //run on each slot and if the slot is empty and there is a card in the deck, place the card from the deck to the slot.
        for (Integer i : slotsList) {
            if (table.slotToCard[i]==-1 && !deck.isEmpty()){
                this.table.beforeWrite();
                table.placeCard(deck.remove(0), i);
                this.table.afterWrite();
                //mark that the table has been changed.
                tableHasBeenChanged = true;
                //every card, update the freeze timer of the players.
                for (Player player : players) {
                    env.ui.setFreeze(player.id, player.timeToFreeze-System.currentTimeMillis());
                }
            }
        }
        //if the table has been changed and hints are enabled
        if (tableHasBeenChanged && env.config.hints){
           this.table.hints(); 
           System.out.println("------------------------------");
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        if (setsToCheck.isEmpty()){
            try {
                this.dealerThread.sleep(1);
            } catch (InterruptedException e) {}
        }
        
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        //GAME MODE: 1
        if (env.config.turnTimeoutMillis<0){
            //creating a list of the card that are on the table.
            List<Integer> integerList1 = Arrays.stream(this.table.slotToCard)
                                            .filter(num -> num != -1)
                                            .collect(Collectors.toList());
            //if there aren't sets on the table, reshuffle.
            if (env.util.findSets(integerList1, 1).isEmpty()){
                reshuffleTime = System.currentTimeMillis()-1;
            }
            else{
                reshuffleTime = Long.MAX_VALUE;
            }
        }

        //GAME MODE: 2 (regular)
        else if (env.config.turnTimeoutMillis>0){
            //if the timer needs to be reseted
            if (reset){
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;
            }
            long timeleft = reshuffleTime-System.currentTimeMillis();
            //ensuring the timer wont display negative numbers.
            if (timeleft<0){
                timeleft = 0;
            }
            //updating the timer
            env.ui.setCountdown(timeleft, reshuffleTime-System.currentTimeMillis()<env.config.turnTimeoutWarningMillis);
        }

        //GAME MODE: 3
        else if (env.config.turnTimeoutMillis==0){
            //if an action was commited, reset the timer.
            if (reset){
                lastActionTime = System.currentTimeMillis();
            }
            env.ui.setCountdown(System.currentTimeMillis()-lastActionTime, false);
            //creating a list of the card that are on the table.
            List<Integer> integerList1 = Arrays.stream(this.table.slotToCard)
                                            .filter(num -> num != -1)
                                            .collect(Collectors.toList());
            //if there aren't sets on the table, reshuffle.
            if (env.util.findSets(integerList1, 1).isEmpty()){
                reshuffleTime = System.currentTimeMillis()-1;
            }
            else{
                reshuffleTime = Long.MAX_VALUE;
            }
        }
        //updating the freezing time of each player.
        for (Player player : players) {
            env.ui.setFreeze(player.id, player.timeToFreeze-System.currentTimeMillis());
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        //creating a random order of cards removing.
        List<Integer> slotsList = new LinkedList<Integer>();
        for (int i = 0 ; i < env.config.tableSize ; i++){
            slotsList.add(i);
        }
        Collections.shuffle(slotsList);

        //remove all of the tokens of the players from the table
        this.table.beforeWrite();
        for (Player p : players) {
            p.waitingForDealerCheck=false;
            p.removeAllPlayerTokens();
        }
        
        //for each card, removes the card from the table and adds it to the deck
        for (Integer slot : slotsList) {
            if (this.table.slotToCard[slot]!=-1){
                deck.add(this.table.slotToCard[slot]);
                this.table.removeCard(slot);
            }
            //every card, update the players freeze timer
            for (Player player : players) {
                env.ui.setFreeze(player.id, player.timeToFreeze-System.currentTimeMillis());
            }
        }
        this.table.afterWrite();
        // after the cards have been collected, shuffle the deck
        Collections.shuffle(deck);
    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
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
    }

    /**
     * Adding a player set to the dealer queue for checking.
     */
    public void addSetToCheck (PlayerSet setToCheck){
        setsToCheck.add(setToCheck);
    }

    /**
     * Waking the dealer thread.
     */
    protected void wakeDealerThread (){
        if (this.dealerThread != null){
            this.dealerThread.interrupt();
        }
    }
}
