package datastructures;

import java.util.concurrent.ConcurrentHashMap;

public class HashAdaptado {

    private ConcurrentHashMap<Long, String> baseDados = new ConcurrentHashMap<>();
    private long size = 0;

    public HashAdaptado() {}

    public void add(String valor) {
        baseDados.put(size, valor);
        size++;
    }

    public String get(Long chave) {
        return baseDados.get(chave);
    }

    public String getLatest() {
        if (baseDados.isEmpty()) {
            return null;
        }
        return baseDados.get(size);
    }

    public String remove(Long chave) {
        if (baseDados.containsKey(chave)) {
            String valor = baseDados.get(chave);
            baseDados.remove(chave);
            size--;
            return valor;
        }
        return null;
    }

    public boolean containsKey(Long chave) {
        return baseDados.containsKey(chave);
    }

    public void limpar() {
        baseDados.clear();
        size = 0;
    }

    public long getTamanho() {
        return baseDados.size();
    }


}