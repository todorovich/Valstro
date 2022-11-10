package net.todorovich;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;
import io.socket.engineio.client.transports.WebSocket;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.Arrays;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main
{
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

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
                        int page = jsonObject.getInt("page");
                        int resultCount = jsonObject.getInt("resultCount");

                        if (page == -1 && page == resultCount)
                        {
                            try {
                                logger.error(jsonObject.getString("error"));
                            } catch (JSONException e) {
                                logger.error("Unexpected error received: " + Arrays.toString(args));
                            }

                            synchronized (mainThread) {
                                mainThread.notify();
                            }
                        }
                        else {

                            logger.info("[" + page + "/" + resultCount + "] " + jsonObject.getString("name"));

                            String[] films = jsonObject.getString("films").split(",");

                            for (String film : films) {
                                logger.info(" - " + film.trim());
                            }
                            logger.info("");

                            if (page == resultCount) {
                                synchronized (mainThread) {
                                    mainThread.notify();
                                }
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
            if (args[0] instanceof JSONObject jsonObject)
            {
                try {
                    logger.error(jsonObject.getString("error"));
                } catch (JSONException e) {
                    logger.error("Unexpected error received: " + Arrays.toString(args));
                }
            }

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
                logger.info("Please Choose a number:\n1.) Search people\n2.) Exit");

                Scanner scanner = new Scanner(System.in);

                String consoleInput = scanner.nextLine();

                if (consoleInput.equalsIgnoreCase("1") || consoleInput.equalsIgnoreCase("s"))
                {
                    logger.info("What name would you like to search for?");
                    scanner = new Scanner(System.in);
                    consoleInput = scanner.nextLine();

                    JSONObject object = new JSONObject();
                    object.put("query", consoleInput);
                    socket.emit("search", object);

                    synchronized (mainThread) {
                        mainThread.wait();
                    }
                }

                else if(
                    consoleInput.equalsIgnoreCase("2") ||
                    consoleInput.equalsIgnoreCase("e")
                )
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
            }

            logger.info("Disconnected from the server.");
        }
    }
}