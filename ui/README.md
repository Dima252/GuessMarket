# The console module of exercise 1

This is the console interface that was submitted for exercise 1, kept as it was.

It no longer takes part in the build. The engine of exercise 2 knows about users:
every request that acts names the user who is acting, events start out closed
until their market maker opens them, and shares are traded either against the
event (LMSR) or through an order book. The console was written against the
single-user engine of exercise 1 and does not compile against that API any more.

Exercise 1 has been submitted and graded on its own jars, so nothing is lost by
leaving it here as a record. Exercise 2 replaces it with the JavaFX application,
which becomes the active module in its place.

`manifest.txt` and `run.bat` are the two files that shipped with it: the manifest
that names its main class and points at the engine jar beside it, and the batch
file the grader ran. They live here now rather than in a folder of their own,
because they belong to this module and to nothing else.
