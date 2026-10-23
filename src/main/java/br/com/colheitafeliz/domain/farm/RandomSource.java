package br.com.colheitafeliz.domain.farm;

@FunctionalInterface
public interface RandomSource { int nextInt(int bound); }
