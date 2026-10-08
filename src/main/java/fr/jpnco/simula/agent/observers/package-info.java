/**
 * Ready-to-use observers shipped with the agent: a console summary ({@link
 * fr.jpnco.simula.agent.observers.SimpleObserver}) and a live JavaFX monitor ({@link
 * fr.jpnco.simula.agent.observers.FxObserver}, {@link fr.jpnco.simula.agent.observers.FxApp},
 * {@link fr.jpnco.simula.agent.observers.MainViewController}) that renders the engine tree,
 * supervision- colored actor boxes fed through {@link
 * fr.jpnco.simula.agent.observers.StatusLedger}, and per-engine lifecycle status.
 *
 * <p>The JavaFX classes are demo code excluded from the coverage gate (they need the toolkit and a
 * display); {@link fr.jpnco.simula.agent.observers.StatusLedger} and {@link
 * fr.jpnco.simula.agent.observers.SimpleObserver} remain under the gate. Implements: FR-005,
 * FR-106, FR-107.
 */
package fr.jpnco.simula.agent.observers;
