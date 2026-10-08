/**
 * The developer-facing API of the agent: capture the root engine ({@link
 * fr.jpnco.simula.agent.api.SimulaTarget}), receive it once ({@link
 * fr.jpnco.simula.agent.api.SimulaObserver}), and stream supervision status changes ({@link
 * fr.jpnco.simula.agent.api.SupervisionWatcher}, {@link
 * fr.jpnco.simula.agent.api.SupervisionEvent}). All types here use the framework's exported
 * interfaces directly; no reflection is required by developer code.
 *
 * <p>Implements: FR-003, FR-004, FR-005, FR-101, FR-102, observer-api and supervision-listener
 * contracts.
 */
package fr.jpnco.simula.agent.api;
