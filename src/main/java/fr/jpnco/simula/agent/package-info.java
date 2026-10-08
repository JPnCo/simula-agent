/**
 * Bootstrap glue of the {@code -javaagent}: the {@link fr.jpnco.simula.agent.SimulaAgent} premain
 * entry point, option parsing, diagnostics logging, the cross-module engine blackboard, and the
 * observer session runner. This package is invisible to developer code; it only wires feature 001's
 * capture pipeline at JVM startup.
 *
 * <p>Implements: FR-001, FR-002, FR-005, FR-006, FR-007, Constitution IX.
 */
package fr.jpnco.simula.agent;
