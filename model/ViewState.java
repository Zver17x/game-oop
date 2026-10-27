package model;

import java.util.ArrayList;
import java.util.List;

public class ViewState {
    public static final int ST_START = 0;
    public static final int ST_PLAY = 1;
    public static final int ST_GAMEOVER = 2;
    public static final int ST_CONNECTING = 3;
    public static final int ST_DISCONNECTED = 4;

    public int status = ST_CONNECTING;
    public int myId = 0;
    public int tick = 0;
    public double time = 0;
    public int connectedCount = 0;
    public int readyCount = 0;
    public List<CarView> cars = new ArrayList<>();
    public List<MeteorView> meteors = new ArrayList<>();

    public CarView find(int id) {
        for (CarView car : cars) if (car.id == id) return car;
        return null;
    }
    public int aliveCount() {
        int result = 0;
        for (CarView car : cars) if (car.alive) result++;
        return result;
    }
}
