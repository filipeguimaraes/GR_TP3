import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Classe que abstrai o acesso ao ficheiro para o resto da aplicação.
 *
 * @author Filipe Miguel Teixeira Freitas Guimarães - A865308
 * @author Joana Isabel Afonso Gomes - A84912
 */
public class EventosDAO {

    /**
     * Ficheiro que contem todos os eventos.
     */
    private File eventFile;

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public EventosDAO(String path) throws IOException {
        eventFile = new File(path);
        if (!eventFile.exists())
            eventFile.createNewFile();

    }


    /**
     * Adiciona um evento ao ficheiro.
     * @param e Evento.
     * @throws IOException Caso não consiga aceder ao ficheiro.
     */
    public void add(Evento e) throws IOException {
        DataOutputStream dataOutputStream = new DataOutputStream(new FileOutputStream(eventFile, true));
        dataOutputStream.write((e.toString() + '\n').getBytes(StandardCharsets.UTF_8));
        dataOutputStream.flush();
        dataOutputStream.close();
    }


    /**
     * Remove um evento no ficheiro pelo nome.
     *
     * @param name Nome do evento.
     * @throws IOException Caso não consiga aceder ao ficheiro.
     */
    public void remove(String name) throws IOException {
        File tempFile = new File(eventFile.getAbsolutePath() + ".tmp");

        BufferedReader br = new BufferedReader(new FileReader(eventFile));
        PrintWriter pw = new PrintWriter(new FileWriter(tempFile));

        String line;

        while ((line = br.readLine()) != null) {
            if (!(getValue(line.trim(),"nome").equals(name))) {
                pw.println(line);
                pw.flush();
            }
        }
        pw.close();
        br.close();

        //Delete the original file
        if (!eventFile.delete()) {
            throw new IOException("Could not delete file");
        }

        //Rename the new file to the filename the original file had.
        if (!tempFile.renameTo(eventFile))
            throw new IOException("Could not rename file");


    }

    /**
     * Método que transforma as linhas do ficheiro numa lista.
     *
     * @return Lista de eventos.
     * @throws IOException Caso não consiga aceder ao ficheiro.
     */
    public List<Evento> getList() throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(eventFile.getPath()));
        List<Evento> eventos = new ArrayList<>();
        String line = br.readLine();
        while (line != null) {
            eventos.add(lineToEvent(line.trim()));
            line = br.readLine();
        }
        br.close();

        eventos.removeIf(Evento::itsTime);

        return eventos;
    }

    /**
     * Tradutor dos campos do ficheiro para objetos do tipo Evento.
     * @param line Linha a processar.
     * @return Evento processado.
     * @throws IOException Caso não consiga aceder ao ficheiro ou obter um campo.
     */
    private Evento lineToEvent(String line) throws IOException {
        Evento evento = new Evento(
                getValue(line, "nome"),
                LocalDateTime.parse(Objects.requireNonNull(getValue(line, "data"))),
                Duration.parse(Objects.requireNonNull(getValue(line, "duracao"))),
                getValue(line, "frasePresente"),
                getValue(line, "fraseFuturo"),
                getValue(line, "frasePassado"));

        try {
            evento.setTimer(Duration.parse(getValue(line,"timer")));
        } catch (IOException ignored){}

        return evento;
    }

    /**
     * Método auxiliar que, usando expressões regulares obtém os valores dos diversos campos do evento.
     * @param line Linha a processar.
     * @param value Campo a obter.
     * @return Valor do campo.
     * @throws IOException Caso não consiga aceder ao ficheiro.
     */
    private String getValue(String line, String value) throws IOException {
        Pattern NAMEpattern = Pattern.compile(value + "=\"[^\"]*\"");
        Matcher NAMEmatcher = NAMEpattern.matcher(line);
        if (NAMEmatcher.find()) {
            String NAMEaux = NAMEmatcher.group();
            Pattern NAME2pattern = Pattern.compile("[^(" + value + "=)\"].*[^\"]");
            Matcher NAME2matcher = NAME2pattern.matcher(NAMEaux);
            if (NAME2matcher.find()) {
                return NAME2matcher.group();
            } throw new IOException("Não foi encontrado o objeto pretendido.");
        } throw new IOException("Não foi encontrado o objeto pretendido.");

    }
}