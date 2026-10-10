package com.github.deripas.receipt.parse;

/**
 * Item with parsed numbers; a number is null if it is not printed or not readable.
 */
record ParsedItem(String name, Double price, Double quantity, Double total) {
}
