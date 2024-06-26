package bguspl.set.ex;

import bguspl.set.Env;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * This class contains the data that is visible to the player.
 *
 * @inv slotToCard[x] == y iff cardToSlot[y] == x
 */
public class Table {

    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Mapping between a slot and the card placed in it (null if none).
     */
    protected final Integer[] slotToCard; // card per slot (if any)

    /**
     * Mapping between a card and the slot it is in (null if none).
     */
    protected final Integer[] cardToSlot; // slot per card (if any)

    /**
     * playersTokens[i][j] = true iff player i has a token in slot j
     */
    protected final boolean[][] playersTokens;

    /**
     * the number of tokens each player gets in the game (equals to feature size)
     */
    protected final int numberOfTokens;

    /**
     * members for RWL
     */
    protected int activeReaders = 0;
    protected int activeWriters = 0;
    protected int waitingWriters = 0;

    /**
     * Constructor for testing.
     *
     * @param env        - the game environment objects.
     * @param slotToCard - mapping between a slot and the card placed in it (null if none).
     * @param cardToSlot - mapping between a card and the slot it is in (null if none).
     */
    public Table(Env env, Integer[] slotToCard, Integer[] cardToSlot) {

        this.env = env;
        this.slotToCard = slotToCard;
        this.cardToSlot = cardToSlot;

        this.playersTokens = new boolean[env.config.players][env.config.tableSize];
        this.numberOfTokens = env.config.featureSize;
    }

    /**
     * Constructor for actual usage.
     *
     * @param env - the game environment objects.
     */
    public Table(Env env) {

        this(env, new Integer[env.config.tableSize], new Integer[env.config.deckSize]);
        // initiallizing the table to be -1 (-1 means that there are no card in the slot / the card is not placed on the table)
        for (int i = 0 ; i < this.slotToCard.length ; i++){
            slotToCard[i]=-1;
        }
        for (int i = 0 ; i < this.cardToSlot.length ; i++){
            cardToSlot[i]=-1;
        }
    }

    /**
     * This method prints all possible legal sets of cards that are currently on the table.
     */
    public void hints() {
        List<Integer> deck = Arrays.stream(slotToCard).filter(Objects::nonNull).filter(i -> i!=-1).collect(Collectors.toList());
        env.util.findSets(deck, Integer.MAX_VALUE).forEach(set -> {
            StringBuilder sb = new StringBuilder().append("Hint: Set found: ");
            List<Integer> slots = Arrays.stream(set).mapToObj(card -> cardToSlot[card]).sorted().collect(Collectors.toList());
            int[][] features = env.util.cardsToFeatures(set);
            System.out.println(sb.append("slots: ").append(slots).append(" features: ").append(Arrays.deepToString(features)));
        });
    }

    /**
     * Count the number of cards currently on the table.
     *
     * @return - the number of cards on the table.
     */
    public int countCards() {
        int cards = 0;
        for (Integer card : slotToCard)
            if (card != null)
                ++cards;
        return cards;
    }

    /**
     * Places a card on the table in a grid slot.
     * @param card - the card id to place in the slot.
     * @param slot - the slot in which the card should be placed.
     *
     * @post - the card placed is on the table, in the assigned slot.
     */
    public void placeCard(int card, int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        cardToSlot[card] = slot;
        slotToCard[slot] = card;

        env.ui.placeCard(card, slot);
    }

    /**
     * Removes a card from a grid slot on the table.
     * @param slot - the slot from which to remove the card.
     */
    public void removeCard(int slot) {
        try {
            Thread.sleep(env.config.tableDelayMillis);
        } catch (InterruptedException ignored) {}

        //checks if there isnt a card in the slot
        if (slotToCard[slot] != -1){
            cardToSlot[slotToCard[slot]] = -1;
            slotToCard[slot] = -1;
        }
        env.ui.removeCard(slot);
    }

    /**
     * Places a player token on a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot on which to place the token.
     */
    public void placeToken(int player, int slot) {
        if (this.slotToCard[slot]!=-1){
            this.playersTokens[player][slot]=true;
            env.ui.placeToken(player, slot);
        }
    }

    /**
     * Removes a token of a player from a grid slot.
     * @param player - the player the token belongs to.
     * @param slot   - the slot from which to remove the token.
     * @return       - true iff a token was successfully removed.
     */
    public boolean removeToken(int player, int slot) {
        if (this.playersTokens[player][slot]){
            playersTokens[player][slot]=false;
            env.ui.removeToken(player, slot);
            return true;
        }
        return false;
    }

    //RWL
    protected synchronized void beforeRead() {
        while (! (waitingWriters == 0 && activeWriters == 0)) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        activeReaders++;
      }
     
    protected synchronized void afterRead()  { 
        activeReaders--;
        notifyAll();  
      }
     
    protected synchronized void beforeWrite() {
        waitingWriters++;
        while (! (activeReaders == 0 && activeWriters == 0)) {
            try {
                wait();
            } catch (InterruptedException e) {
            }
        }
        waitingWriters--;
        activeWriters++;
      }
     
    protected synchronized void afterWrite() { 
        activeWriters--;
        notifyAll(); 
      }
}
