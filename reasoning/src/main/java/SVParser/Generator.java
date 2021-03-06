package SVParser;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Generator {
    public static ArrayList<SensitiveMethod> sensitiveMethods = new ArrayList<>();

    public static int fsNum = 0;
    public static int netstateNum = 0;
    public static int protomsgNum = 0;
    public static int dbNum = 0;

    public static Matcher parse(String line, String pattern){
        Pattern r = Pattern.compile(pattern);
        Matcher m = r.matcher(line);
        return m;
    }

    public static boolean check(String condition, String consequence){
        if (condition.contains("+") || consequence.contains("+")) return true;
        String cond = condition.split(" ->")[0];
        String cons = consequence.split(" ->")[0];
        return cons.startsWith(cond);

    }

    public static String concatenate(String condition, String consequence, String DFMethod, int status) {
        String observation = "";
        switch (status) {
            case 3:
                consequence = String.format("%s#%s","$fs::@ -> write $var_",consequence.split("#")[1]);
                if (condition.contains("fs")) {
                    Matcher matcher = Pattern.compile("(.+)::(.+) ->(.+)").matcher(condition);
                    matcher.find();
                    consequence = consequence.replace("@",matcher.group(2));
                }
                else if (condition.contains("net_state")) consequence = consequence.replace("@","var_#");
                else break;
            case 0: case 1:
                if (check(condition,consequence)) break;
                else observation = String.format("if %s then %s { %s }",condition,consequence,DFMethod);
            case 2:
                break;

        }
        return observation;
    }

    public static void generate() throws IOException {
        BufferedReader file = new BufferedReader(new FileReader("backwardFlow"));
        ArrayList<String> content = new ArrayList<>();
        String line;
        while ((line = file.readLine()) != null){
            if (line.length()==0) continue;
            line = line.trim();
            //System.out.println(line);
            content.add(line);
        }

        SensitiveMethod sensitiveMethod = null;
        int idx = 0;
        while (idx < content.size()) {
            if (content.get(idx).startsWith("-")) {
                if (sensitiveMethod!=null){
                    sensitiveMethods.add(sensitiveMethod);
                    sensitiveMethod = null;
                }
                idx++;
                continue;
            }
            if (sensitiveMethod == null){
                sensitiveMethod = new SensitiveMethod();
                Matcher methodInfo = parse(content.get(idx+1),"<(.+)> at Line (.+)");
                if (methodInfo.find()) {
                    sensitiveMethod.setMethodName(methodInfo.group(1));
                    sensitiveMethod.setLineNumber(methodInfo.group(2));
                } else assert false;

                Matcher consequence = parse(content.get(idx+2),"Consequence: (.+)");
                if (consequence.find()) {
                    sensitiveMethod.setConsequence(consequence.group(1));
                } else assert false;

                Matcher sensitiveUsage = parse(content.get((idx+3)), "Usage: (.+)");
                if (sensitiveUsage.find()) {
                    sensitiveMethod.setSpecialCase(sensitiveUsage.group(1));
                } else {
                    sensitiveMethod.setSpecialCase(sensitiveMethod.getMethodName().split(":")[1].trim());
                }
                idx = idx + 5;
            } else {
                SourceData sourceData = new SourceData();
                Matcher pid = parse(content.get(idx+1),"pid: (.+)");
                if (pid.find()) {
                    sourceData.setPid(pid.group(1).replaceAll("^\\.|\"|\\.$",""));
                } else assert false;

                Matcher DFMethod = parse(content.get(idx+2), "DFMethod: (.+)");
                if (DFMethod.find()) {
                    if (sourceData.setDFMethod(sensitiveMethod,DFMethod.group(1)))
                        sensitiveMethods.add(new SensitiveMethod(sensitiveMethod, new SourceData(sensitiveMethod,DFMethod.group(1))));

                } else assert false;

                Matcher dataTypeAndOperation = parse(content.get(idx+3),"DataType & operation: (.+)");
                if (dataTypeAndOperation.find()){
                    sourceData.setDataType(dataTypeAndOperation.group(1).split("=")[0]);
                    sourceData.setOperation(dataTypeAndOperation.group(1).split("=")[1]);
                } else assert false;

                Matcher sinkData = parse(content.get(idx+4),"sinkData: \\[(.+)\\]");
                if (sinkData.find()){
                    sourceData.setSpecialCase(sinkData.group(1));
                } else assert false;

                sensitiveMethod.addSinkData(sourceData);
                idx = idx + 5;
            }
        }

        ArrayList<String> observations = new ArrayList<>();
        PrintWriter pw = new PrintWriter("observation.txt");

        for (SensitiveMethod sm: sensitiveMethods) {
            String consequence = sm.getConsequence();
            int status = 0;
            if (sm.getSpecialCase()) status = 2;
            for (SourceData sd: sm.getSourceDataList()) {
                String condition = sd.getCondition();
                String DFMethod = sd.getDFMethod();
                String observation;
                if (sd.getSpecialCase()) observation = concatenate(condition,consequence, DFMethod,status + 1);
                else observation = concatenate(condition, consequence, DFMethod, status);
                if (observation.length() > 0 && observations.contains(observation)==false) {
                    observations.add(observation);
                    if (sd.getDataType().equals("fs")) fsNum += 1;
                    if (sd.getDataType().equals("db")) dbNum += 1;
                    if (sd.getDataType().equals("net_state")) netstateNum += 1;
                    if (sd.getDataType().equals("proto_msg")) protomsgNum += 1;
                }

            }
        }
        for (idx = 0; idx < observations.size(); idx += 1) {
            pw.write(observations.get(idx).replaceAll("#", Integer.toString(idx)));
            pw.write("\n\n");
        }
        pw.close();
        System.out.println(String.format("Number of total poisoning events: %d\nNumber of DataType fs: %d\nNumber of DataType proto_msg: %d\nNumber of DataType net_state: %d\nNumber of DataType db: %d\n",observations.size(),fsNum,protomsgNum,netstateNum,dbNum));

    }
}
