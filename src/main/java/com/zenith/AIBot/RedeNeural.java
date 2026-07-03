package com.zenith.AIBot;

import javax.annotation.Nonnull;
import java.io.*;
import java.util.Random;
import java.util.Scanner;

public class RedeNeural {
    int entradas = 5; // valores que ele vai receber (vida, inimigos, quantidade de madeira)
    int ocultos = 4; // camada de pensamento
    int saidas = 5; // quantos estados ele pode escolher

    double[][] pesosEntradaParaOculta; // Entrada --> Oculta
    double[][] pesosOcultaParaSaida; //  Oculta --> Saida

    double[] humorCamadaOculta;
    double[] humorCamadaSaida;

    double velocidadeAprendizado = 0.15;

    float recompensaTotal = 0;

    String nomeDoBot;

    public RedeNeural(String bot) {
        nomeDoBot = bot;
        pesosEntradaParaOculta = new double[ocultos][entradas];
        pesosOcultaParaSaida = new double[saidas][ocultos];
        humorCamadaOculta = new double[ocultos];
        humorCamadaSaida = new double[saidas];

        Random geradorAleatorio = new Random();

        for (int numNeuronio = 0; numNeuronio < ocultos; numNeuronio++) {
            for (int numInfo = 0; numInfo < entradas; numInfo++) {
                pesosEntradaParaOculta[numNeuronio][numInfo] = geradorAleatorio.nextDouble() - 0.5;

            }
            humorCamadaOculta[numNeuronio] = geradorAleatorio.nextDouble() - 0.5;
        }

        for (int numSaida = 0; numSaida < saidas; numSaida++) {
            for (int numNeuronio2 = 0; numNeuronio2 < ocultos; numNeuronio2++) {
                pesosOcultaParaSaida[numSaida][numNeuronio2] = geradorAleatorio.nextDouble() - 0.5;
            }
            humorCamadaSaida[numSaida] = geradorAleatorio.nextDouble() - 0.5;
        }
    }

    private double sigmoid(double valor) {
        return 1 / (1 + Math.exp(-valor));
    }

    private double derivadaSigmoid(double valorEspremido) {
        // calcula qual estado está apontando
        return valorEspremido * (1 - valorEspremido);
    }

    @Nonnull
    private double[] calcularNotasDasAcoes(double vida, int inimigos, int madeiras, double fome, int pedras) {
        double[] dadosDaEntrada = {vida / 20.0, inimigos / 10.0, madeiras / 64.0, fome / 20.0, pedras / 64.0};

        double[] ativacaoOculta = new double[ocultos];
        // cria uma lista com os neuronios ^^^
        for (int numNeuronio = 0; numNeuronio < ocultos; numNeuronio++) { // para cada neuronio oculto
            double somaDoNeuronio = humorCamadaOculta[numNeuronio];
            // pega o humor daquele neuronio ^^^

            for (int numInfo = 0; numInfo < entradas; numInfo++) { // para cada informação daquele neuronio
                // calcula a ativação daquele neuronio sem espremer
                somaDoNeuronio += pesosEntradaParaOculta[numNeuronio][numInfo] * dadosDaEntrada[numInfo];
            }

            // espreme a ativação daquele neuronio para obter a ativação real
            ativacaoOculta[numNeuronio] = sigmoid(somaDoNeuronio);
        }

        double[] notasFinaisSaida = new double[saidas];
        // ^^^ cria uma lista com as notas de cada neuronio
        for (int numSaida = 0; numSaida < saidas; numSaida++) {
            // para cada saida faça o que está embaixo vvv

            double somaDaSaida = humorCamadaSaida[numSaida];
            // pega o humor daquela saida ^^^

            for (int numNeuronio = 0; numNeuronio < ocultos; numNeuronio++) {
                // soma (humor + (peso daquela saida * ativação do neuronio) )
                somaDaSaida += pesosOcultaParaSaida[numSaida][numNeuronio] * ativacaoOculta[numNeuronio];
            }
            // espreme a nota final
            notasFinaisSaida[numSaida] = sigmoid(somaDaSaida);
        }
        return notasFinaisSaida;
    }


    public int decidirAcao(Object[] inputs) {
        double vida = ((Number) inputs[0]).doubleValue();
        int inimigos = ((Number) inputs[1]).intValue();
        int madeiras = ((Number) inputs[2]).intValue();
        double fome = ((Number) inputs[3]).doubleValue();
        int pedras = ((Number) inputs[4]).intValue();

        double[] notaDasAcoes = calcularNotasDasAcoes(vida, inimigos, madeiras, fome, pedras);
        int melhorAcao = 0;

        for (int acao = 1; acao < notaDasAcoes.length; acao++) {
            if (notaDasAcoes[acao] > notaDasAcoes[melhorAcao]) {
                melhorAcao = acao;
            }
        }
        return melhorAcao;
    }

    public void recompensar(Object[] inputs, int acaoTomada, double recompensa) {
        double vida = ((Number) inputs[0]).doubleValue();
        int inimigos = ((Number) inputs[1]).intValue();
        int madeiras = ((Number) inputs[2]).intValue();
        double fome = ((Number) inputs[3]).doubleValue();
        int pedras = ((Number) inputs[4]).intValue();

        double[] dadosInput = {vida / 20.0, inimigos / 10.0, madeiras / 64.0, fome / 20.0, pedras / 64.0};
        double[] ativacaoOculta = new double[ocultos];
        double[] entradaOculta = new double[ocultos];
        recompensaTotal = 0;

        for (int neuronio = 0; neuronio < ocultos; neuronio++) {
            Double soma = humorCamadaOculta[neuronio];
            for (int info = 0; info < entradas; info++) {
                soma += pesosEntradaParaOculta[neuronio][info] * dadosInput[info];
            }
            entradaOculta[neuronio] = soma;
            ativacaoOculta[neuronio] = sigmoid(soma);
        }

        double[] notaDasAcoes = calcularNotasDasAcoes(vida, inimigos, madeiras, fome, pedras);

        double objetivo = (recompensa + 1) / 2;
        double ajusteNecessario = objetivo - notaDasAcoes[acaoTomada];
        double mudancaNessecaria = ajusteNecessario * derivadaSigmoid(notaDasAcoes[acaoTomada]);

        for (int numNeuronio = 0; numNeuronio < ocultos; numNeuronio++) {
            pesosOcultaParaSaida[acaoTomada][numNeuronio] += velocidadeAprendizado * mudancaNessecaria * ativacaoOculta[numNeuronio];
        }
        humorCamadaSaida[acaoTomada] += velocidadeAprendizado * mudancaNessecaria;

        double[] mudancaNessecariaOculta = new double[ocultos];
        for (int i = 0; i < ocultos; i++) {
            double ajusteNecessarioOculto = mudancaNessecaria * pesosOcultaParaSaida[acaoTomada][i];
            mudancaNessecariaOculta[i] = ajusteNecessarioOculto * derivadaSigmoid(ativacaoOculta[i]);
        }

        for (int i = 0; i < ocultos; i++) {
            for (int j = 0; j < entradas; j++) {
                pesosEntradaParaOculta[i][j] += velocidadeAprendizado * mudancaNessecariaOculta[i] * dadosInput[j];
            }
            humorCamadaOculta[i] += velocidadeAprendizado * mudancaNessecariaOculta[i];
        }

        recompensaTotal += recompensa;
        salvarMemoria();
    }

    public String printStatus(Object[] inputs) {
        double vida = ((Number) inputs[0]).doubleValue();
        int inimigos = ((Number) inputs[1]).intValue();
        int madeiras = ((Number) inputs[2]).intValue();
        double fome = ((Number) inputs[3]).doubleValue();
        int pedras = ((Number) inputs[4]).intValue();

        double[] notas = calcularNotasDasAcoes(vida, inimigos, madeiras, fome, pedras);

        return ("\n| Estado Atual: " + Bots.PegarBot(nomeDoBot).estadoAtual + "\n| Nota Ataque: " + (float)notas[0] + "\n| Nota Defesa: " + (float)notas[1] + "\n| Nota Madeira: " + (float)notas[2] + "\n| Nota Fome: " + (float)notas[3] + "\n| Vida: " + (float)vida + "\n| Fome: " + (float)fome + "\n| Madeiras: " + madeiras + "\n| Pedras: " + pedras + "\n| Inimigos: " + inimigos + "\n| Recompensa: " + recompensaTotal);
    }

    public void salvarMemoria() {

        try (PrintWriter gravador = new PrintWriter(new FileWriter(nomeDoBot + ".txt"))) {
            for (int i = 0; i < ocultos; i++) {
                for (int j = 0; j < entradas; j++) {
                    gravador.println(pesosEntradaParaOculta[i][j]);
                }
                gravador.println(humorCamadaOculta[i]);
            }

            for (int i = 0; i < saidas; i++) {
                for (int j = 0; j < ocultos; j++) {
                    gravador.println(pesosOcultaParaSaida[i][j]);
                }
                gravador.println(humorCamadaSaida[i]);
            }

        } catch (IOException e) {
            System.out.println("[Erro IA] Não conseguiu salvar os pesos: " + e.getMessage());
        }

    }

    public void carregarMemoria() {
        File arquivo = new File(nomeDoBot + ".txt");
        if (!arquivo.exists()) {
            return;
        }
        try (Scanner leitor = new Scanner(arquivo)) {
            for (int i = 0; i < ocultos; i++) {
                for (int j = 0; j < entradas; j++) {
                    if (leitor.hasNextDouble()) pesosEntradaParaOculta[i][j] = leitor.nextDouble();
                }
                if (leitor.hasNextDouble()) humorCamadaOculta[i] = leitor.nextDouble();
            }

            for (int i = 0; i < saidas; i++) {
                for (int j = 0; j < ocultos; j++) {
                    if (leitor.hasNextDouble()) pesosOcultaParaSaida[i][j] = leitor.nextDouble();
                }
                if (leitor.hasNextDouble()) humorCamadaSaida[i] = leitor.nextDouble();
            }

        } catch (FileNotFoundException e) {
            System.out.println("[Erro IA] Não conseguiu carregar os pesos: " + e.getMessage());
        }
    }
}
