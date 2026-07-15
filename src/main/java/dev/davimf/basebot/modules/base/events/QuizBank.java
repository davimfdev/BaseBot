package dev.davimf.basebot.modules.base.events;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/** Banco estático de perguntas de quiz, multi-tema e com alternativas embaralháveis. */
public final class QuizBank {

    /** Pergunta com 5 alternativas; {@code correct} é o índice da opção certa. */
    public record Question(String text, List<String> options, int correct) {
        /** Retorna uma cópia com as alternativas embaralhadas e o índice correto recalculado. */
        public Question shuffled(Random r) {
            String answer = options.get(correct);
            List<String> mixed = new ArrayList<>(options);
            Collections.shuffle(mixed, r);
            return new Question(text, List.copyOf(mixed), mixed.indexOf(answer));
        }
    }

    private static Question q(String text, int correct, String... options) {
        return new Question(text, List.of(options), correct);
    }

    private static final List<Question> QUESTIONS = List.of(
            // --- Geografia ---
            q("Qual é o maior país do mundo em área territorial?", 2,
                    "Canadá", "China", "Rússia", "Estados Unidos", "Brasil"),
            q("Qual país tem a maior população do mundo?", 1,
                    "China", "Índia", "Estados Unidos", "Indonésia", "Paquistão"),
            q("Qual é o menor país do mundo em área?", 1,
                    "Mônaco", "Vaticano", "Nauru", "San Marino", "Malta"),
            q("Em que continente fica o deserto do Saara?", 1,
                    "Ásia", "África", "Oceania", "América do Sul", "Europa"),
            q("Qual é a capital da Austrália?", 2,
                    "Sydney", "Melbourne", "Canberra", "Perth", "Brisbane"),
            q("Qual país europeu tem formato semelhante a uma bota?", 1,
                    "Grécia", "Itália", "Espanha", "Portugal", "Croácia"),
            q("Qual é a cordilheira mais extensa do mundo?", 0,
                    "Andes", "Himalaia", "Alpes", "Montanhas Rochosas", "Cáucaso"),
            q("Qual é o maior deserto quente do mundo?", 0,
                    "Saara", "Gobi", "Kalahari", "Atacama", "Arábico"),

            // --- História ---
            q("Em que ano começou a Segunda Guerra Mundial?", 1,
                    "1914", "1939", "1945", "1929", "1918"),
            q("Quem foi o primeiro imperador romano?", 1,
                    "Júlio César", "Augusto", "Nero", "Constantino", "Trajano"),
            q("Em que ano o ser humano pisou na Lua pela primeira vez?", 0,
                    "1969", "1972", "1965", "1959", "1971"),
            q("Qual civilização construiu Machu Picchu?", 2,
                    "Astecas", "Maias", "Incas", "Olmecas", "Toltecas"),
            q("Em que ano foi proclamada a independência do Brasil?", 1,
                    "1808", "1822", "1889", "1500", "1824"),
            q("Qual muro histórico caiu em 1989?", 1,
                    "Muralha da China", "Muro de Berlim", "Muro das Lamentações", "Muro de Adriano", "Muro de Cnossos"),
            q("Quem se tornou líder de Cuba após a revolução de 1959?", 1,
                    "Che Guevara", "Fidel Castro", "Fulgencio Batista", "Salvador Allende", "León Trótski"),
            q("Qual país foi o berço dos Jogos Olímpicos na Antiguidade?", 2,
                    "Egito", "Roma", "Grécia", "Pérsia", "China"),

            // --- Química ---
            q("Qual é o símbolo químico do ouro?", 0,
                    "Au", "Ag", "Fe", "Or", "Go"),
            q("Qual é o elemento químico mais abundante no universo?", 1,
                    "Oxigênio", "Hidrogênio", "Hélio", "Carbono", "Nitrogênio"),
            q("Qual metal é líquido à temperatura ambiente?", 0,
                    "Mercúrio", "Chumbo", "Ferro", "Sódio", "Estanho"),
            q("Qual é o pH de uma solução neutra?", 1,
                    "0", "7", "14", "1", "10"),
            q("Quantos elementos há na tabela periódica atual?", 0,
                    "118", "92", "100", "108", "120"),
            q("Qual é a fórmula química da água?", 0,
                    "H₂O", "CO₂", "O₂", "H₂O₂", "NaCl"),
            q("Qual gás as plantas absorvem durante a fotossíntese?", 1,
                    "Oxigênio", "Gás carbônico", "Nitrogênio", "Hidrogênio", "Metano"),

            // --- Física / Astronomia ---
            q("Qual é a velocidade da luz no vácuo (aproximada)?", 0,
                    "300 mil km/s", "300 mil km/h", "30 mil km/s", "3 milhões km/s", "150 mil km/s"),
            q("Qual partícula subatômica tem carga negativa?", 2,
                    "Próton", "Nêutron", "Elétron", "Fóton", "Pósitron"),
            q("Qual é o maior planeta do Sistema Solar?", 1,
                    "Saturno", "Júpiter", "Netuno", "Urano", "Terra"),
            q("Qual planeta é conhecido por seus anéis proeminentes?", 2,
                    "Júpiter", "Urano", "Saturno", "Netuno", "Marte"),
            q("Qual é a estrela mais próxima da Terra?", 2,
                    "Sirius", "Alfa Centauri", "Sol", "Proxima Centauri", "Vega"),
            q("Quantos planetas há no Sistema Solar?", 0,
                    "8", "9", "7", "10", "12"),
            q("Qual é o planeta mais próximo do Sol?", 1,
                    "Vênus", "Mercúrio", "Terra", "Marte", "Júpiter"),
            q("Qual é o planeta mais quente do Sistema Solar?", 1,
                    "Mercúrio", "Vênus", "Marte", "Júpiter", "Terra"),

            // --- Biologia ---
            q("Qual é o maior órgão do corpo humano?", 1,
                    "Fígado", "Pele", "Pulmão", "Cérebro", "Intestino"),
            q("Quantos ossos tem o corpo humano adulto?", 0,
                    "206", "300", "180", "250", "195"),
            q("Qual organela é conhecida como a 'usina de energia' da célula?", 1,
                    "Núcleo", "Mitocôndria", "Ribossomo", "Lisossomo", "Complexo de Golgi"),
            q("Qual é o maior animal já conhecido na Terra?", 1,
                    "Elefante-africano", "Baleia-azul", "Tubarão-baleia", "Girafa", "Lula-gigante"),
            q("Qual vitamina o corpo produz com a exposição ao sol?", 1,
                    "Vitamina C", "Vitamina D", "Vitamina A", "Vitamina B12", "Vitamina K"),
            q("Quantas câmaras (cavidades) tem o coração humano?", 2,
                    "2", "3", "4", "5", "6"),
            q("Qual é o menor osso do corpo humano?", 0,
                    "Estribo", "Fêmur", "Rádio", "Falange", "Martelo"),

            // --- Literatura ---
            q("Quem escreveu 'Dom Casmurro'?", 1,
                    "José de Alencar", "Machado de Assis", "Jorge Amado", "Clarice Lispector", "Graciliano Ramos"),
            q("Quem escreveu 'Romeu e Julieta'?", 0,
                    "William Shakespeare", "Dante Alighieri", "Miguel de Cervantes", "Goethe", "Molière"),
            q("Qual autor criou o detetive Sherlock Holmes?", 1,
                    "Agatha Christie", "Arthur Conan Doyle", "Edgar Allan Poe", "Júlio Verne", "Stephen King"),
            q("Quem escreveu 'Dom Quixote'?", 0,
                    "Miguel de Cervantes", "Luís de Camões", "Federico García Lorca", "Jorge Luis Borges", "Gabriel García Márquez"),
            q("Quem escreveu 'A Divina Comédia'?", 0,
                    "Dante Alighieri", "Petrarca", "Boccaccio", "Virgílio", "Homero"),
            q("Qual destas obras é de Machado de Assis?", 1,
                    "O Cortiço", "Memórias Póstumas de Brás Cubas", "Iracema", "Vidas Secas", "Grande Sertão: Veredas"),

            // --- Artes e Música ---
            q("Quem pintou a 'Mona Lisa'?", 1,
                    "Michelangelo", "Leonardo da Vinci", "Van Gogh", "Pablo Picasso", "Rafael"),
            q("Quem pintou 'A Noite Estrelada'?", 1,
                    "Claude Monet", "Vincent van Gogh", "Salvador Dalí", "Pablo Picasso", "Paul Cézanne"),
            q("Qual movimento artístico Pablo Picasso ajudou a fundar?", 1,
                    "Impressionismo", "Cubismo", "Surrealismo", "Expressionismo", "Barroco"),
            q("Quantas cordas tem um violino tradicional?", 0,
                    "4", "5", "6", "7", "3"),
            q("Quem compôs a 'Nona Sinfonia' (Ode à Alegria)?", 2,
                    "Mozart", "Bach", "Beethoven", "Chopin", "Vivaldi"),

            // --- Tecnologia / Computação ---
            q("O que significa a sigla 'CPU'?", 1,
                    "Central Process Unit", "Central Processing Unit", "Computer Personal Unit", "Central Power Unit", "Core Processing Unit"),
            q("Quantos bits há em um byte?", 1,
                    "4", "8", "16", "32", "2"),
            q("Qual empresa desenvolveu o sistema operacional Android?", 2,
                    "Apple", "Microsoft", "Google", "Samsung", "IBM"),
            q("Em qual sistema numérico os computadores operam internamente?", 1,
                    "Decimal", "Binário", "Hexadecimal", "Octal", "Romano"),
            q("Quem cofundou a Microsoft junto com Paul Allen?", 1,
                    "Steve Jobs", "Bill Gates", "Mark Zuckerberg", "Elon Musk", "Larry Page"),
            q("O que significa a sigla 'HTML'?", 0,
                    "HyperText Markup Language", "HighText Markup Language", "HyperText Making Language", "Home Tool Markup Language", "HyperText Machine Language"),
            q("Qual linguagem de programação foi criada por James Gosling?", 1,
                    "Python", "Java", "C++", "Ruby", "PHP"),
            q("Quantos bytes há em um kilobyte (padrão binário)?", 1,
                    "1000", "1024", "512", "2048", "100"),

            // --- Cinema ---
            q("Qual filme de James Cameron retrata o naufrágio de um transatlântico em 1912?", 1,
                    "Avatar", "Titanic", "O Aviador", "Poseidon", "Náufrago"),
            q("Quem dirigiu o filme 'Pulp Fiction'?", 1,
                    "Martin Scorsese", "Quentin Tarantino", "Steven Spielberg", "Christopher Nolan", "David Fincher"),
            q("Em qual franquia aparece o personagem Darth Vader?", 1,
                    "Star Trek", "Star Wars", "Guardiões da Galáxia", "Duna", "Interestelar"),

            // --- Esportes ---
            q("Quantos jogadores de cada time ficam em campo no futebol?", 2,
                    "9", "10", "11", "12", "7"),
            q("Quantas vezes o Brasil foi campeão da Copa do Mundo masculina?", 1,
                    "4", "5", "6", "3", "7"),
            q("Em qual esporte se usa o termo 'nocaute'?", 1,
                    "Tênis", "Boxe", "Vôlei", "Natação", "Golfe"),
            q("De quantos em quantos anos ocorrem os Jogos Olímpicos de verão?", 2,
                    "2", "3", "4", "5", "6"),
            q("Quantos pontos vale uma cesta de três no basquete?", 2,
                    "1", "2", "3", "4", "5"),

            // --- Mitologia ---
            q("Quem é o deus grego dos mares?", 1,
                    "Zeus", "Poseidon", "Hades", "Ares", "Apolo"),
            q("Na mitologia nórdica, quem é o deus do trovão?", 2,
                    "Odin", "Loki", "Thor", "Freyr", "Balder"),
            q("Qual criatura grega tem cabeça de touro e corpo de homem?", 1,
                    "Centauro", "Minotauro", "Ciclope", "Quimera", "Medusa"),
            q("Na mitologia grega, quem é o rei dos deuses?", 1,
                    "Cronos", "Zeus", "Hades", "Hermes", "Apolo"),

            // --- Lógica / Matemática ---
            q("Quanto é 15% de 200?", 1,
                    "15", "30", "20", "45", "25"),
            q("Quanto vale a soma dos ângulos internos de um triângulo?", 1,
                    "90°", "180°", "270°", "360°", "120°"),
            q("Qual destes números é primo?", 2,
                    "21", "27", "29", "33", "51"),
            q("Quanto é 2 elevado a 10?", 1,
                    "512", "1024", "256", "2048", "100"),
            q("Qual é a raiz quadrada de 144?", 1,
                    "11", "12", "13", "14", "16"),
            q("Quantos lados tem um dodecágono?", 1,
                    "10", "12", "8", "20", "15"),
            q("Qual é o valor aproximado de π (pi)?", 0,
                    "3,14", "3,41", "2,17", "1,62", "6,28"),

            // --- Curiosidades gerais ---
            q("Qual é o idioma mais falado como língua nativa no mundo?", 1,
                    "Inglês", "Mandarim", "Espanhol", "Hindi", "Árabe"),
            q("Qual metal é o melhor condutor de eletricidade?", 2,
                    "Cobre", "Ouro", "Prata", "Alumínio", "Ferro"),
            q("Quantas cores tem tradicionalmente o arco-íris?", 2,
                    "5", "6", "7", "8", "9"));

    private QuizBank() {}

    /** Sorteia uma pergunta com as alternativas já embaralhadas. */
    public static Question random() {
        Question base = QUESTIONS.get(ThreadLocalRandom.current().nextInt(QUESTIONS.size()));
        return base.shuffled(ThreadLocalRandom.current());
    }

    /** Exposto para testes: todas as perguntas cruas (sem embaralhar). */
    static List<Question> all() {
        return QUESTIONS;
    }
}
