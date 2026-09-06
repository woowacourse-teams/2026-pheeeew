package com.pheeeew.sigh.experiment.e001;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;

final class E001Distribution {

    private static final List<E001Scenario> TUNING = List.of(E001Scenario.TUNING_SINGLE, E001Scenario.TUNING_GRID);
    private static final List<E001Scenario> CONFIRMATION = List.of(E001Scenario.CONFIRMATION_SINGLE_500,
            E001Scenario.CONFIRMATION_SINGLE_5000, E001Scenario.CONFIRMATION_GRID_500, E001Scenario.CONFIRMATION_GRID_5000);
    private static final List<E001Scenario> SPECTRAL = List.of(E001Scenario.SPECTRAL_EQUAL, E001Scenario.SPECTRAL_IMBALANCED);

    private final Evaluator evaluator;
    private final ToDoubleFunction<E001Parameters> calibration;
    private final Map<E001Parameters, Map<E001Scenario, E001Trial>> trials = new LinkedHashMap<>();
    private final Map<E001Parameters, Double> intensities = new LinkedHashMap<>();

    private E001Distribution(Evaluator evaluator, ToDoubleFunction<E001Parameters> calibration) {
        this.evaluator = evaluator;
        this.calibration = calibration;
    }

    static Result run(Evaluator evaluator, ToDoubleFunction<E001Parameters> calibration) {
        return new E001Distribution(evaluator, calibration).execute();
    }

    private Result execute() {
        E001Parameters a = E001Parameters.distance("A", 0);
        if (!evaluate(a, TUNING, null, true)) {
            return finish(E001Selection.tune(candidate(a), List.of(), List.of()));
        }
        List<E001Parameters> ds = List.of(E001Parameters.distance("D", 100), E001Parameters.distance("D", 120));
        for (E001Parameters parameters : ds) {
            evaluate(parameters, TUNING, null, false);
        }
        E001Selection.Decision selection = E001Selection.tune(candidate(a), ds.stream().map(this::candidate).toList(), List.of());
        if (selection.status() == E001Selection.Status.INCONCLUSIVE) {
            return finish(selection);
        }
        E001Parameters d = ds.stream().filter(p -> p.parameterSetId().equals(selection.d().parameterSetId())).findFirst().orElseThrow();
        List<E001Parameters> es = E001Parameters.fieldCandidates(d.sigma());
        for (E001Parameters parameters : es) {
            evaluate(parameters, TUNING, d, false);
        }
        E001Selection.Decision tuning = E001Selection.tune(candidate(a), ds.stream().map(this::candidate).toList(),
                es.stream().map(this::candidate).toList());
        E001Parameters e = tuning.e() == null ? null : es.stream()
                .filter(p -> p.parameterSetId().equals(tuning.e().parameterSetId())).findFirst().orElseThrow();
        E001Parameters b = E001Parameters.distance("B", 0);
        E001Parameters c = E001Parameters.distance("C", d.sigma());
        for (E001Parameters control : List.of(a, b, c, d)) {
            if (!evaluate(control, CONFIRMATION, null, true)) {
                return finish(E001Selection.confirm(tuning, candidate(a), candidate(b), candidate(c), candidate(d), candidate(e)));
            }
        }
        if (CONFIRMATION.stream().filter(scenario -> scenario.halfWidth() == 300)
                .anyMatch(scenario -> !E001Selection.passesD(candidate(d).scenario(scenario.id())))) {
            return finish(E001Selection.confirm(tuning, candidate(a), candidate(b), candidate(c), candidate(d), candidate(e)));
        }
        if (e != null) {
            evaluate(e, CONFIRMATION, d, true);
        }
        E001Selection.Decision confirmation = E001Selection.confirm(tuning,
                candidate(a), candidate(b), candidate(c), candidate(d), candidate(e));
        if (confirmation.status() != E001Selection.Status.SPECTRAL_REQUIRED) {
            return finish(confirmation);
        }
        for (E001Parameters control : List.of(a, d)) {
            if (!evaluate(control, SPECTRAL, null, true)) {
                return finish(E001Selection.spectral(confirmation, candidate(a), candidate(d), candidate(e)));
            }
        }
        if (SPECTRAL.stream().allMatch(scenario ->
                Double.isFinite(candidate(d).scenario(scenario.id()).pooledValue("grid300")))) {
            evaluate(e, SPECTRAL, d, true);
        }
        return finish(E001Selection.spectral(confirmation, candidate(a), candidate(d), candidate(e)));
    }

    private boolean evaluate(E001Parameters parameters, List<E001Scenario> scenarios,
                             E001Parameters baseline, boolean stopOnIntegrityFailure) {
        intensities.computeIfAbsent(parameters, p -> p.modelId().equals("E") ? calibration.applyAsDouble(p) : 0.0);
        Map<E001Scenario, E001Trial> results = trials.computeIfAbsent(parameters, ignored -> new LinkedHashMap<>());
        boolean valid = true;
        for (E001Scenario scenario : scenarios) {
            E001Trial reference = baseline == null ? null : trials.get(baseline).get(scenario);
            E001Scenario.Plan plan = scenario.plan();
            E001Trial trial = evaluator.evaluate(parameters, plan, reference);
            if (!trial.batch().parameters().equals(parameters) || !trial.batch().plan().equals(plan)
                    || results.putIfAbsent(scenario, trial) != null) {
                throw new IllegalStateException("실험 결과는 요청한 파라미터와 시나리오에 한 번만 연결해야 해요.");
            }
            if (!trial.evaluation().integrityPassed()) {
                valid = false;
                if (stopOnIntegrityFailure) {
                    break;
                }
            }
        }
        return valid;
    }

    private E001Candidate candidate(E001Parameters parameters) {
        if (parameters == null) {
            return null;
        }
        Map<String, E001Evaluation> evaluations = new HashMap<>();
        trials.getOrDefault(parameters, Map.of()).forEach((scenario, trial) -> evaluations.put(scenario.id(), trial.evaluation()));
        return E001Candidate.of(parameters.parameterSetId(), parameters.sigma(), parameters.sampler(),
                intensities.getOrDefault(parameters, 0.0), evaluations);
    }

    private Result finish(E001Selection.Decision decision) {
        List<E001Trial> completed = new ArrayList<>();
        trials.values().forEach(byScenario -> completed.addAll(byScenario.values()));
        return Result.of(decision, completed, intensities);
    }

    @FunctionalInterface
    interface Evaluator {

        E001Trial evaluate(E001Parameters parameters, E001Scenario.Plan plan, E001Trial baseline);
    }

    record Result(E001Selection.Decision decision, List<E001Trial> trials, Map<E001Parameters, Double> intensities) {

        Result {
            trials = List.copyOf(trials);
            intensities = Map.copyOf(intensities);
        }

        static Result of(E001Selection.Decision decision, List<E001Trial> trials, Map<E001Parameters, Double> intensities) {
            return new Result(decision, trials, intensities);
        }

        long requestedCount() {
            return trials.stream().mapToLong(trial -> trial.batch().plan().requestedCount()).sum();
        }

        long generatedCount() {
            return trials.stream().mapToLong(trial -> trial.batch().samples().size()).sum();
        }

        long failureCount() {
            return trials.stream().mapToLong(trial -> trial.batch().failures().size()).sum();
        }
    }
}
