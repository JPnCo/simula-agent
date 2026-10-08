package fr.jpnco.simula.agent.api;

import fr.jpnco.simula.Actor;
import fr.jpnco.simula.Engine;
import fr.jpnco.simula.actors.SimulaSupervisor;

/**
 * One actor lifecycle status change recorded by the engine supervision (FR-101).
 *
 * <p>{@code previous == null} marks the first time the supervisor recorded the actor; {@code
 * current == null} marks an actor whose supervision disappeared — either the entry vanished from
 * the supervisor state map between two watcher cycles (supervision listener contract, point 4), or
 * the supervisor itself left the engine tree (engine stopped), in which case every last known
 * status is reported once as a disappearance.
 *
 * <p>This class is part of the instrumentation agent; it is not part of the simula contract.
 *
 * <p>Implements: FR-101.
 *
 * @param engine the engine whose supervisor recorded the change
 * @param actor the actor whose status changed
 * @param previous the status before the change, {@code null} on first record
 * @param current the status after the change, {@code null} on disappearance
 */
public record SupervisionEvent(
    Engine engine,
    Actor actor,
    SimulaSupervisor.Status previous,
    SimulaSupervisor.Status current) {}
