package com.boydti.fawe.util.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The plain-text page footer under //help and other paged lists. It must name the command with the
 * next page number, and there must be no footer once the last page is shown.
 */
class MessagePageFooterTest {

    @Test
    void firstOfSeveralPagesPointsAtPageTwo() {
        assertEquals("/help 2", Message.nextPageCommand("/help", 1, 3));
    }

    @Test
    void middlePagePointsAtTheNextOne() {
        assertEquals("/help 3", Message.nextPageCommand("/help", 2, 3));
    }

    @Test
    void lastPageHasNoNextCommand() {
        assertNull(Message.nextPageCommand("/help", 3, 3));
    }

    @Test
    void singlePageHasNoNextCommand() {
        assertNull(Message.nextPageCommand("/help", 1, 1));
    }

    @Test
    void overviewPointsAtPageOne() {
        assertEquals("/help 1", Message.nextPageCommand("/help", 0, 1));
    }
}
