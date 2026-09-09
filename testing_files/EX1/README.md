# Exercise 1 — course check files

Supplied with the course for exercise 1 and kept untouched. They are in the **v1
format**, which has no `GM-users` element, so the exercise 2 program refuses them
on purpose and says why. They are here as a record of what exercise 1 was graded
against, and because the LMSR simulation is still the reference for that half of
the mathematics.

| File | What it is |
|---|---|
| `GM-EX1-Schema.xsd` | The v1 schema. It spells the commission element `comision` with one m; the v2 schema spells it correctly, and the loader accepts both. |
| `single.xml` | One valid LMSR event. |
| `multiple.xml` | Several valid LMSR events. |
| `error-2.xml`, `error-3.xml` | The two faulty files exercise 1 was checked against. |
| `lmsr_simulation.html` | The interactive LMSR walk-through. The numbers are deterministic, and the engine still reproduces them — see the appendix A checks in `verification/Verify.java`. |

The files for exercise 2 are in [../EX2/](../EX2/).
