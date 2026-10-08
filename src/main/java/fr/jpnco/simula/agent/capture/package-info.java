/**
 * The load-time instrumentation of the agent: the Byte Buddy transformer and its inline advice
 * templates that capture root engine construction and force framework supervision. Advice bodies
 * reference {@code java.base} types only, so transformed framework classes inside the named {@code
 * simula} module never link to agent code; any signature drift degrades to a logged no-op.
 *
 * <p>Implements: FR-002, FR-008, agent-options contract ({@code supervise}), capture contract,
 * Constitution IX.
 */
package fr.jpnco.simula.agent.capture;
