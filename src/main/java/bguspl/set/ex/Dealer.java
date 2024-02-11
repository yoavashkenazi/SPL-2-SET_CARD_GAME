package bguspl.set.ex;

import bguspl.set.Env;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
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
    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */
    @Override
    public void run() {
        this.dealerThread = Thread.currentThread();
        env.logger.info("thread " + Thread.currentThread().getName() + " starting.");
        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            updateTimerDisplay(true);
            removeAllCardsFromTable();
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
        
        /// before checking a set, we need to check if the set is still relevent
        // remove tokens
        for (int player = 0 ; player < playersTokens.length ; player++ ) {
            if (playersTokens[player][slot]){
                this.removeToken(this.id, slot);
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
                table.placeCard(deck.remove(0), i);
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some purpose.
     */
    private void sleepUntilWokenOrTimeout() {
        // TODO implement
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {}
    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {
        // TODO implement
        //if the timer needs to be reseted
        if (reset){
            reshuffleTime = reshuffleTime + 60000;
            env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(), reshuffleTime-System.currentTimeMillis()<=10);
        }
        //else, update the timer
        else{
            env.ui.setCountdown(reshuffleTime-System.currentTimeMillis(), reshuffleTime-System.currentTimeMillis()<=10);
        }
    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        int i = 0;
        //for each card, removes the card from the table and adds it to the deck
        for (Integer cardId : table.slotToCard) {
            table.removeCard(i);
            deck.add(cardId);
            i++;
        }
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
    }

    public void addSetToCheck (PlayerSet setToCheck){
        setsToCheck.add(setToCheck);
    }

    protected void wakeDealerThread (){
        if (this.dealerThread != null){
            this.dealerThread.interrupt();
        }
    }
}
