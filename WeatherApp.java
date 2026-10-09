import java.util.*;
import javax.swing.*;

class Sensor {
    String id; double limit;
    Sensor(String i,double l){id=i;limit=l;}
}
class Reading {
    String id; Double value; long time;
    Reading(String i,Double v){id=i;value=v;time=System.currentTimeMillis();}
    public String toString(){return id+" = "+String.format("%.1f",value);}
}
public class WeatherApp implements Runnable {
    static HashMap<String,Sensor> sensors=new HashMap<>();
    static ArrayList<Reading> readings=new ArrayList<>();
    static LinkedList<String> alerts=new LinkedList<>();
    static HashMap<String,Reading> latest=new HashMap<>();
    static TreeMap<Long,Reading> byTime=new TreeMap<>();
    static Double[] hourly=new Double[24];
    static boolean running=false;
    static JTextField id=new JTextField(6),lim=new JTextField(5);
    static JTextArea out=new JTextArea(15,50);

   // public void run(){
      //  while(true){
          //  try{Thread.sleep(1000);}catch(Exception e){}
          //  if(running)log();
       // }
   // }
    static void log(){
        for(Sensor s:sensors.values()){
            Reading r=new Reading(s.id,Math.random()*60);
            readings.add(r); latest.put(s.id,r); byTime.put(r.time,r);
            hourly[Calendar.getInstance().get(Calendar.HOUR_OF_DAY)]=r.value;
            if(r.value>s.limit)alerts.addFirst("ALERT: "+r);
        }
        show();
    }
    static void show(){
        String x="LATEST\n";
        for(Reading r:latest.values())x+=r+"\n";
        x+="\nALERTS\n";
        for(String a:alerts)x+=a+"\n";
        out.setText(x);
    }
    static void add(){
        try{
            String s=id.getText(); double l=Double.parseDouble(lim.getText());
            if(sensors.containsKey(s)||l<0||l>100)throw new Exception("Invalid input");
            sensors.put(s,new Sensor(s,l));
        }catch(Exception e){JOptionPane.showMessageDialog(null,e.getMessage());}
    }
    static void delete(){
        String s=id.getText();
        if(sensors.remove(s)==null)JOptionPane.showMessageDialog(null,"Not found");
        else latest.remove(s);
    }
    static void search(){
        String q=id.getText(),x="SEARCH\n";
        for(Reading r:readings)if(r.id.equals(q))x+=r+"\n";
        out.setText(x);
    }
    static void sort(){
        ArrayList<Reading> a=new ArrayList<>(readings);
        Collections.sort(a,(x,y)->x.value.compareTo(y.value));
        String s="SORTED\n";for(Reading r:a)s+=r+"\n";out.setText(s);
    }
    public static void main(String[] z){
        new Thread(new WeatherApp()).start();
        JFrame f=new JFrame("Weather Station"); JPanel p=new JPanel();
        p.add(new JLabel("ID"));p.add(id);p.add(new JLabel("Limit"));p.add(lim);
        JButton a=new JButton("Add"),d=new JButton("Delete"),st=new JButton("Start/Stop");
        JButton se=new JButton("Search"),so=new JButton("Sort");
        a.addActionListener(e->add());d.addActionListener(e->delete());
        st.addActionListener(e->running=!running);se.addActionListener(e->search());
        so.addActionListener(e->sort());
        p.add(a);p.add(d);p.add(st);p.add(se);p.add(so);
        f.add(p,"North");f.add(new JScrollPane(out));f.setSize(800,500);
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);f.setVisible(true);
    }
}