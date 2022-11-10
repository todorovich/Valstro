package net.todorovich;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{
    private static Logger logger = LoggerFactory.getLogger(Main.class);

    private static final Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            logger.debug("onConnect received:" + Arrays.toString(args));
            synchronized (mainThread) {
                mainThread.notify();
            }
        }
    };

    private static final Emitter.Listener onDisconnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            logger.debug("onDisconnect received:" + Arrays.toString(args));
            synchronized (mainThread) {
                mainThread.notify();
            }
        }
    };

    private static final Emitter.Listener onSearch = new Emitter.Listener() {
        @Override
        public void call(Object... args)
        {
            for (Object Object : args)
            {
                if (Object instanceof JSONObject jsonObject)
                {
                    try
                    {
                        logger.info(jsonObject.getString("name"));

                        String[] films = jsonObject.getString("films").split(",");

                        for (String film : films)
                        {
                            logger.info(" - " + film.trim());
                        }
                        logger.info("");

                        if (jsonObject.getInt("page") == jsonObject.getInt("resultCount")) {
                            synchronized (mainThread) {
                                mainThread.notify();
                            }
                        }
                    }
                    catch (JSONException e)
                    {
                        logger.error("Encountered a JSON Exception trying to parse " + Arrays.toString(args));

                        synchronized (mainThread) {
                            mainThread.notify();
                        }
                    }
                }
            }
        }
    };

    private static final Emitter.Listener onError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            logger.error("onError received:" + Arrays.toString(args));
            synchronized (mainThread) {
                mainThread.notify();
            }
        }
    };

    private static final Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            logger.debug("onConnectError received:" + Arrays.toString(args));
            synchronized (mainThread) {
                mainThread.notify();
            }
        }
    };

    static final Thread mainThread = Thread.currentThread();

    public static void main(String[] args)
    {
        URI uri = URI.create("http://0.0.0.0:3000");

        IO.Options options = IO.Options.builder()
                .setTransports(new String[] {WebSocket.NAME})
                //.setQuery("{query: \"Luke\"}")
                //.setPath("/api/people/")
                .build();

        Socket socket = null;
        try {
            socket = IO.socket(uri, options);

            socket.on("connect_error", onConnectError);

            socket.on("connect", onConnect);

            socket.on("disconnect", onDisconnect);

            socket.on("search", onSearch);

            socket.on("error", onError);

            socket.connect();

            synchronized (mainThread) {
                mainThread.wait();
            }

            boolean exit = false;
            do
            {
                logger.info("Please Choose:\n1.) search people\n2.) exit");

                Scanner scanner = new Scanner(System.in);

                String consoleInput = scanner.nextLine();

                if (consoleInput.equalsIgnoreCase("1") || consoleInput.equalsIgnoreCase("search"))
                {
                    logger.info("What is your query?");
                    scanner = new Scanner(System.in);
                    consoleInput = scanner.nextLine();

                    JSONObject object = new JSONObject();
                    object.put("query", consoleInput);
                    String json = object.toString();
                    socket.emit("search", object);

                    synchronized (mainThread) {
                        mainThread.wait();
                    }
                }

                else if(consoleInput.equalsIgnoreCase("2") || consoleInput.equalsIgnoreCase("exit"))
                    exit = true;

            } while (!exit);
        }
        catch (JSONException | InterruptedException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            logger.info("Disconnecting from the server...");

            if (socket != null)
            {
                socket.disconnect();
                socket.off();
                socket = null;
            }

            logger.info("Disconnected from the server.");
        }
    }
}