import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.*;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.StringReader;

public class CachacaScriptIDE {
    private JFrame frame;
    private JTextPane txtCodigo;
    private JTextArea txtSaida;
    private JTree treeAST;
    private JTable tableTokens;
    private DefaultTableModel tableModelTokens;
    private JTextArea txtLinhas;
    private JButton btnCompilar;
    private JButton btnRodar;
    private ASTNode ultimoAstRoot = null;
    private static compiladorCachacaScript parser = null;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {
                try {
                    // Set System look and feel for a clean native appearance
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}
                new CachacaScriptIDE().init();
            }
        });
    }

    public void init() {
        // Principal Frame
        frame = new JFrame("CachaçaScript IDE - Edição com Árvore Sintática e Análise Léxica");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(950, 680);
        frame.setLayout(new BorderLayout());

        // Line Counter JTextArea
        txtLinhas = new JTextArea("1");
        txtLinhas.setFont(new Font("Consolas", Font.PLAIN, 14));
        txtLinhas.setBackground(new Color(230, 230, 230));
        txtLinhas.setForeground(Color.GRAY);
        txtLinhas.setEditable(false);
        txtLinhas.setFocusable(false);
        txtLinhas.setBorder(BorderFactory.createEmptyBorder(1, 5, 0, 5));

        // Code Editor
        txtCodigo = new JTextPane();
        txtCodigo.setFont(new Font("Consolas", Font.PLAIN, 14));
        txtCodigo.setBorder(BorderFactory.createEmptyBorder(1, 2, 0, 0));

        // JTextPane tab configuration
        SimpleAttributeSet tabs = new SimpleAttributeSet();
        StyleConstants.setTabSet(tabs, new TabSet(new TabStop[] {
            new TabStop(32), new TabStop(64), new TabStop(96), new TabStop(128),
            new TabStop(160), new TabStop(192), new TabStop(224), new TabStop(256)
        }));
        txtCodigo.setParagraphAttributes(tabs, false);

        // Document Listener to sync Line Numbers and apply syntax highlighting
        txtCodigo.getDocument().addDocumentListener(new DocumentListener() {
            private String getLineNumbersText() {
                Element root = txtCodigo.getDocument().getDefaultRootElement();
                StringBuilder text = new StringBuilder("1");
                for (int i = 2; i <= root.getElementCount(); i++) {
                    text.append("\n").append(i);
                }
                return text.toString();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                txtLinhas.setText(getLineNumbersText());
                aplicarHighlight();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                txtLinhas.setText(getLineNumbersText());
                aplicarHighlight();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                txtLinhas.setText(getLineNumbersText());
            }
        });

        JScrollPane scrollCodigo = new JScrollPane(txtCodigo);
        scrollCodigo.setRowHeaderView(txtLinhas);
        
        // JTabbedPane for AST Tree and Tokens list
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: AST JTree with Expand/Collapse buttons
        JPanel panelAST = new JPanel(new BorderLayout());
        JPanel panelBotoesAST = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
        JButton btnExpandir = new JButton("Expandir Tudo");
        JButton btnColapsar = new JButton("Colapsar Tudo");
        btnExpandir.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnColapsar.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        panelBotoesAST.add(btnExpandir);
        panelBotoesAST.add(btnColapsar);
        panelAST.add(panelBotoesAST, BorderLayout.NORTH);

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Árvore Sintática (AST)");
        treeAST = new JTree(rootNode);
        treeAST.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JScrollPane scrollTree = new JScrollPane(treeAST);
        panelAST.add(scrollTree, BorderLayout.CENTER);
        
        btnExpandir.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                expandAll(treeAST);
            }
        });
        btnColapsar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                collapseAll(treeAST);
            }
        });
        
        tabbedPane.addTab("🌳 Árvore Sintática (AST)", panelAST);

        // Tab 2: Tokens JTable
        String[] columnNames = {"Lexema", "Tipo de Token", "Linha", "Coluna"};
        tableModelTokens = new DefaultTableModel(columnNames, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        tableTokens = new JTable(tableModelTokens);
        tableTokens.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        tableTokens.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        JScrollPane scrollTable = new JScrollPane(tableTokens);
        tabbedPane.addTab("📋 Tabela de Tokens", scrollTable);

        // Console Output
        txtSaida = new JTextArea();
        txtSaida.setEditable(false);
        txtSaida.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtSaida.setBackground(new Color(245, 245, 245));
        JScrollPane scrollSaida = new JScrollPane(txtSaida);

        // Top Horizontal Split (Code Editor on left, Tabs on right)
        JSplitPane topSplitPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            scrollCodigo,
            tabbedPane
        );
        topSplitPane.setDividerLocation(480);
        topSplitPane.setResizeWeight(0.6);

        // Main Vertical Split (Editor+Tabs on top, Console on bottom)
        JSplitPane mainSplitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            topSplitPane,
            scrollSaida
        );
        mainSplitPane.setDividerLocation(430);
        mainSplitPane.setResizeWeight(0.65);

        frame.add(mainSplitPane, BorderLayout.CENTER);

        // Header Control Panel
        JPanel panelSuperior = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
        btnCompilar = new JButton("🍺 Virar Dose (Compilar)");
        btnCompilar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnCompilar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                executarCompilacao();
            }
        });
        panelSuperior.add(btnCompilar);

        JButton btnGerarGCC = new JButton("🛠️ Gerar Executável (GCC)");
        btnGerarGCC.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnGerarGCC.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                gerarCodigoGCC();
            }
        });
        panelSuperior.add(btnGerarGCC);

        btnRodar = new JButton("🚀 Rodar Programa");
        btnRodar.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnRodar.setEnabled(false);
        btnRodar.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                rodarPrograma();
            }
        });
        panelSuperior.add(btnRodar);

        frame.add(panelSuperior, BorderLayout.NORTH);

        // Load a default test structure
        txtCodigo.setText(
            "abreAButelada\n" +
            "  \n" +
            "  alambique doseInteira soma abreCopo doseInteira a maisUmaDose doseInteira b fechaCopo\n" +
            "  abreBarril\n" +
            "    devolveDose a + b viraDose\n" +
            "  fechaBarril\n" +
            "  \n" +
            "  abreBarril\n" +
            "    doseInteira resultado viraDose\n" +
            "    resultado engarrafar soma abreCopo 5 maisUmaDose 3 fechaCopo viraDose\n" +
            "    serveNoCopo abreCopo resultado fechaCopo viraDose\n" +
            "  fechaBarril\n" +
            "fechaAButelada"
        );
        aplicarHighlight();

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void destacarLinhaErro(int linha) {
        try {
            int startOffset = getLineStartOffset(txtCodigo, linha - 1);
            int endOffset = getLineEndOffset(txtCodigo, linha - 1);
            Highlighter.HighlightPainter painter = 
                new DefaultHighlighter.DefaultHighlightPainter(new Color(255, 200, 200));
            txtCodigo.getHighlighter().addHighlight(startOffset, endOffset, painter);
        } catch (BadLocationException e) {
            // ignore
        }
    }

    private int getLineStartOffset(JTextPane textPane, int lineIndex) {
        String text = textPane.getText();
        int currentLine = 0;
        int offset = 0;
        while (currentLine < lineIndex && offset < text.length()) {
            int nextNL = text.indexOf('\n', offset);
            if (nextNL == -1) {
                return text.length();
            }
            offset = nextNL + 1;
            currentLine++;
        }
        return offset;
    }
    
    private int getLineEndOffset(JTextPane textPane, int lineIndex) {
        String text = textPane.getText();
        int start = getLineStartOffset(textPane, lineIndex);
        int nextNL = text.indexOf('\n', start);
        if (nextNL == -1) {
            return text.length();
        }
        return nextNL;
    }

    private void executarCompilacao() {
        txtSaida.setText("");
        // Remove todos os destaques antigos
        txtCodigo.getHighlighter().removeAllHighlights();
        // Limpa a tabela de tokens
        tableModelTokens.setRowCount(0);

        String conteudo = txtCodigo.getText();

        // Limpa a lista de erros antes de começar a compilação
        compiladorCachacaScript.listaErros.clear();
        compiladorCachacaScript.linhasComErros.clear();

        // Inicializa o parser estático uma única vez caso ainda não tenha sido
        if (parser == null) {
            parser = new compiladorCachacaScript(new StringReader(""));
        }

        // 1. Fase Léxica: Extração e listagem de todos os tokens na tabela
        try {
            StringReader lexReader = new StringReader(conteudo);
            compiladorCachacaScript.ReInit(lexReader);
            
            Token tok = compiladorCachacaScript.getNextToken();
            while (tok != null && tok.kind != compiladorCachacaScriptConstants.EOF) {
                String lexeme = tok.image;
                String typeName = compiladorCachacaScriptConstants.tokenImage[tok.kind];
                // Limpa aspas do nome gerado pelo JavaCC
                if (typeName.startsWith("\"") && typeName.endsWith("\"")) {
                    typeName = typeName.substring(1, typeName.length() - 1);
                }
                tableModelTokens.addRow(new Object[]{
                    lexeme,
                    typeName,
                    tok.beginLine,
                    tok.beginColumn
                });
                tok = compiladorCachacaScript.getNextToken();
            }
        } catch (TokenMgrError lexError) {
            // Os erros léxicos serão capturados e reportados pelo compilador principal
        } catch (Exception ignored) {}

        // 2. Fase Sintática e AST
        StringReader parserReader = new StringReader(conteudo);
        compiladorCachacaScript.ReInit(parserReader);

        try {
            txtSaida.append("Lendo programa CachaçaScript da tela...\n");
            
            // Execute parser and get AST root node
            ASTNode astRoot = compiladorCachacaScript.Programa();
            
            // Verifica se houve erros recuperados
            if (compiladorCachacaScript.listaErros.isEmpty()) {
                txtSaida.append("Sucesso: Programa sintaticamente correto.\n");
                ultimoAstRoot = astRoot;
            } else {
                ultimoAstRoot = null;
                txtSaida.append("🍺 Ih rapaz... Encontrei " + compiladorCachacaScript.listaErros.size() + " erro(s) sintático(s):\n");
                for (String err : compiladorCachacaScript.listaErros) {
                    txtSaida.append(" - " + err + "\n");
                }
                // Destaca todas as linhas com erros recuperados
                for (int line : compiladorCachacaScript.linhasComErros) {
                    destacarLinhaErro(line);
                }
            }
            
            // Populate Swing Tree with the AST (even if it has error nodes)
            if (astRoot != null) {
                treeAST.setModel(new DefaultTreeModel(astRoot.toSwingTree()));
                // Expand JTree nodes automatically
                for (int i = 0; i < treeAST.getRowCount(); i++) {
                    treeAST.expandRow(i);
                }
            } else {
                treeAST.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Programa Vazio")));
            }
        } 
        catch (ParseException e) {
            ultimoAstRoot = null;
            // Isso acontece se um erro catastrófico não recuperado pelo modo pânico ocorrer
            treeAST.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Sem Árvore (Erro Sintático Crítico)")));
            txtSaida.append("🍺 Opa! Um erro crítico impediu a recuperação do parser...\n");
            Token t = e.currentToken != null ? e.currentToken.next : null;
            if (t != null) {
                destacarLinhaErro(t.beginLine);
                txtSaida.append("Erro sintático na linha " + t.beginLine + ", coluna " + t.beginColumn + "\n");
                txtSaida.append("Token problemático: " + t.image + "\n");
            }
            txtSaida.append("Detalhes: " + e.getMessage() + "\n");
        } 
        catch (TokenMgrError e) {
            ultimoAstRoot = null;
            treeAST.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Sem Árvore (Erro Léxico)")));
            txtSaida.append("🍺 Ih rapaz... serviram um caractere estranho no balcão.\n");
            txtSaida.append("Erro léxico: encontrei algo que não pertence à linguagem CachaçaScript.\n");
            txtSaida.append("Detalhes: " + e.getMessage() + "\n");

            // Tenta extrair a linha do erro léxico usando regex
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("line (\\d+)");
            java.util.regex.Matcher m = p.matcher(e.getMessage());
            if (m.find()) {
                try {
                    int line = Integer.parseInt(m.group(1));
                    destacarLinhaErro(line);
                } catch (Exception ignored) {}
            }
        }
    }

    private void expandAll(JTree tree) {
        int i = 0;
        while (i < tree.getRowCount()) {
            tree.expandRow(i);
            i++;
        }
    }

    private void collapseAll(JTree tree) {
        // Collapse everything except the root node
        for (int i = tree.getRowCount() - 1; i >= 1; i--) {
            tree.collapseRow(i);
        }
    }

    private void gerarCodigoGCC() {
        // 1. Run compilation phase first to update the AST and make sure code is valid
        executarCompilacao();
        
        // 2. Check if compilation was successful
        if (ultimoAstRoot == null || !compiladorCachacaScript.listaErros.isEmpty()) {
            txtSaida.append("\n❌ Erro: Não é possível gerar o código C/GCC pois existem erros sintáticos ou léxicos no código-fonte.\n");
            btnRodar.setEnabled(false);
            return;
        }
        
        try {
            txtSaida.append("\n[GCC] Iniciando a geração de código destino...\n");
            // 3. Generate C code
            String codigoC = CodeGenerator.generate(ultimoAstRoot);
            
            // 4. Save C code to a file
            java.io.File fileC = new java.io.File("codigo.c");
            java.io.FileWriter writer = new java.io.FileWriter(fileC);
            writer.write(codigoC);
            writer.close();
            txtSaida.append("[GCC] Código C gerado com sucesso e salvo em 'codigo.c'.\n");
            
            // 5. Run GCC compiler
            txtSaida.append("[GCC] Compilando com GCC: gcc codigo.c -o programa.exe ...\n");
            ProcessBuilder pb = new ProcessBuilder("gcc", "codigo.c", "-o", "programa.exe");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            
            // Read GCC output
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(proc.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                txtSaida.append("[GCC Output] " + line + "\n");
            }
            
            int exitCode = proc.waitFor();
            if (exitCode == 0) {
                txtSaida.append("🎉 [GCC] Compilação realizada com sucesso! 'programa.exe' criado.\n");
                btnRodar.setEnabled(true);
            } else {
                txtSaida.append("❌ [GCC] Erro na compilação GCC. Código de saída: " + exitCode + "\n");
                btnRodar.setEnabled(false);
            }
        } catch (Exception ex) {
            txtSaida.append("❌ [GCC] Ocorreu uma exceção ao gerar/compilar com o GCC: " + ex.getMessage() + "\n");
            btnRodar.setEnabled(false);
        }
    }

    private void rodarPrograma() {
        try {
            java.io.File exeFile = new java.io.File("programa.exe");
            if (!exeFile.exists()) {
                txtSaida.append("\n❌ Erro: O arquivo 'programa.exe' não foi encontrado. Compile primeiro.\n");
                return;
            }
            txtSaida.append("\n🚀 Iniciando 'programa.exe' em um novo terminal do Windows...\n");
            Runtime.getRuntime().exec("cmd.exe /c start cmd /k programa.exe");
        } catch (Exception ex) {
            txtSaida.append("❌ Erro ao executar o programa: " + ex.getMessage() + "\n");
        }
    }

    private boolean isColoring = false;
    
    private void aplicarHighlight() {
        if (isColoring) return;
        isColoring = true;
        
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                try {
                    StyledDocument doc = txtCodigo.getStyledDocument();
                    String text = doc.getText(0, doc.getLength());
                    
                    // Reset all styles to default (black, regular font)
                    SimpleAttributeSet styleNormal = new SimpleAttributeSet();
                    StyleConstants.setForeground(styleNormal, Color.BLACK);
                    StyleConstants.setBold(styleNormal, false);
                    doc.setCharacterAttributes(0, doc.getLength(), styleNormal, true);
                    
                    // Style for keywords (blue and bold)
                    SimpleAttributeSet styleKeyword = new SimpleAttributeSet();
                    StyleConstants.setForeground(styleKeyword, new Color(30, 80, 180));
                    StyleConstants.setBold(styleKeyword, true);
                    
                    // Style for types (dark green and bold)
                    SimpleAttributeSet styleType = new SimpleAttributeSet();
                    StyleConstants.setForeground(styleType, new Color(46, 139, 87));
                    StyleConstants.setBold(styleType, true);

                    // Style for comments (gray, italic)
                    SimpleAttributeSet styleComment = new SimpleAttributeSet();
                    StyleConstants.setForeground(styleComment, Color.GRAY);
                    StyleConstants.setItalic(styleComment, true);

                    // Style for literal values (dark brown)
                    SimpleAttributeSet styleLiteral = new SimpleAttributeSet();
                    StyleConstants.setForeground(styleLiteral, new Color(165, 42, 42));
                    
                    // Define lists of keywords and types
                    String[] keywords = {
                        "abreAButelada", "fechaAButelada", "abreBarril", "fechaBarril",
                        "alambique", "seDerBoa", "seDerRuim", "enquantoTemCana", "tomaUma",
                        "ateFicarTonto", "rodada", "devolveDose", "engarrafar", "serveNoCopo",
                        "pedeNoBalcao", "maisUmaDose", "viraDose", "simPatrao", "nemAPau"
                    };
                    
                    String[] types = {
                        "doseInteira", "doseQuebrada", "taNoGrau", "rotulo", "garrafa", "semDose"
                    };

                    // Keywords highlighting
                    for (String kw : keywords) {
                        highlightPattern(doc, "\\b" + kw + "\\b", styleKeyword);
                    }
                    
                    // Types highlighting
                    for (String type : types) {
                        highlightPattern(doc, "\\b" + type + "\\b", styleType);
                    }
                    
                    // Comments: // to end of line
                    highlightPattern(doc, "//.*", styleComment);
                    
                    // Strings: "..."
                    highlightPattern(doc, "\"[^\"]*\"", styleLiteral);
                    
                    // Numbers
                    highlightPattern(doc, "\\b\\d+(\\.\\d+)?\\b", styleLiteral);

                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    isColoring = false;
                }
            }
        });
    }

    private void highlightPattern(StyledDocument doc, String patternStr, AttributeSet attr) throws Exception {
        String text = doc.getText(0, doc.getLength());
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr);
        java.util.regex.Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            doc.setCharacterAttributes(matcher.start(), matcher.end() - matcher.start(), attr, false);
        }
    }
}
