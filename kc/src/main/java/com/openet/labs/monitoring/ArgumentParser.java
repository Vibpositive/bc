package com.openet.labs.monitoring;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class ArgumentParser {
    List<String> arguments;
    HashMap<String, List<String>> argumentsHashMap = new HashMap<>();
    Set<String> flags = new HashSet<>();
    private static final Logger logger = LoggerFactory.getLogger(ArgumentParser.class);

    public ArgumentParser(String[] arguments) {
        this.arguments = Arrays.asList(arguments);
        map();
    }

    public Set<String> getArgumentNames() {
        Set<String> argumentNames = new HashSet<>();
        argumentNames.addAll(flags);
        argumentNames.addAll(argumentsHashMap.keySet());
        return argumentNames;
    }

//    public boolean getFlag(String flagName)
//    {
//        return flags.contains(flagName);
//    }

    public String getArgumentValue(String argumentName) {
        return argumentsHashMap.containsKey(argumentName) ? argumentsHashMap.get(argumentName).get(0) : null;
    }

    private void help(){
        logger.info("This is how you use it");
    }

    public void map() {
        if (arguments.size() < 4){
            help();
            exit("Incorrect number of parameters ", 1);
        }
        for(String argument: arguments) {
            if(argument.startsWith("--")) {
//                if (args.indexOf(arg) == (args.size() - 1))
//                {
////                    exit();
//                    flags.add(arg.replace("--", ""));
//                }
//                else if (args.get(args.indexOf(arg)+1).startsWith("--"))
//                if (arguments.get(arguments.indexOf(arg)+1).startsWith("--")) {
//                    flags.add(arg.replace("--", ""));
//                } else {
                    //List of values (can be multiple)
                    List<String> argumentValues = new ArrayList<>();
                    int i = 1;
                    while(arguments.indexOf(argument)+i != arguments.size() && !arguments.get(arguments.indexOf(argument)+i).startsWith("--")) {
                        argumentValues.add(arguments.get(arguments.indexOf(argument)+i));
                        argumentsHashMap.put(argument.replace("--", ""), argumentValues);
                        i++;
                    }
//                }
            }
        }
    }

    private void exit(String message, int code) {
        System.out.println("Exiting program " + message);
        logger.info("Exiting program with message: {} and code: {}", message, code);
        System.exit(code);
    }
}