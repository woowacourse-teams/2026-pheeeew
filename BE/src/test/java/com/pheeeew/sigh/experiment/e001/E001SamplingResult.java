package com.pheeeew.sigh.experiment.e001;

sealed interface E001SamplingResult permits E001SamplingResult.Success, E001SamplingResult.Failure {

    int proposalCount();

    boolean succeeded();

    record Success(E001Offset offset, int proposalCount) implements E001SamplingResult {

        static Success of(E001Offset offset, int proposalCount) {
            return new Success(offset, proposalCount);
        }

        @Override
        public boolean succeeded() {
            return true;
        }
    }

    record Failure(int proposalCount) implements E001SamplingResult {

        static Failure from(int proposalCount) {
            return new Failure(proposalCount);
        }

        @Override
        public boolean succeeded() {
            return false;
        }
    }
}
