package com.dealstracker.chat;

import java.util.List;

/** A language model that answers the conversation; returns text to show the shopper, never throws. */
interface ChatModel {

    String reply(String instructions, List<ChatMessage> conversation);
}
