#!/usr/bin/env python3
"""Guessing game: guess whether the random number is 0 or 1."""

import random
import sys


def read_guess() -> int:
    while True:
        try:
            raw = input("Guess 0 or 1: ").strip()
        except EOFError:
            print("\nNo input received, exiting.")
            sys.exit(1)
        if raw in ("0", "1"):
            return int(raw)
        print("Invalid input, please enter 0 or 1.")


def play() -> bool:
    guess = read_guess()
    drawn = random.randint(0, 1)
    print(f"The number was {drawn}.")
    won = guess == drawn
    print("You win!" if won else "You lose!")
    return won


def main() -> None:
    play()


if __name__ == "__main__":
    main()
