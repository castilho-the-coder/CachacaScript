import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.text.Element;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.DefaultMutableTreeNode;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.StringReader;

public class CachacaScriptIDE {
    private JFrame frame;
    private JTextArea txtCodigo;
    private JTextArea txtSaida;
    private JTree treeAST;
    private JTable tableTokens;
    private DefaultTableModel tableModelTokens;
    private JTextArea txtLinhas;
    private JButton btnCompilar;
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
        txtCodigo = new JTextArea();
        txtCodigo.setFont(new Font("Consolas", Font.PLAIN, 14));
        txtCodigo.setTabSize(4);
        txtCodigo.setBorder(BorderFactory.createEmptyBorder(1, 2, 0, 0));

        // Document Listener to sync Line Numbers
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
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                txtLinhas.setText(getLineNumbersText());
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

        // Tab 1: AST JTree
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Árvore Sintática (AST)");
        treeAST = new JTree(rootNode);
        treeAST.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JScrollPane scrollTree = new JScrollPane(treeAST);
        tabbedPane.addTab("🌳 Árvore Sintática (AST)", scrollTree);

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

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void destacarLinhaErro(int linha) {
        try {
            int startOffset = txtCodigo.getLineStartOffset(linha - 1);
            int endOffset = txtCodigo.getLineEndOffset(linha - 1);
            javax.swing.text.Highlighter.HighlightPainter painter = 
                new javax.swing.text.DefaultHighlighter.DefaultHighlightPainter(new Color(255, 200, 200));
            txtCodigo.getHighlighter().addHighlight(startOffset, endOffset, painter);
        } catch (javax.swing.text.BadLocationException e) {
            // ignore
        }
    }

    private void executarCompilacao() {
        txtSaida.setText("");
        // Remove todos os destaques antigos
        txtCodigo.getHighlighter().removeAllHighlights();
        // Limpa a tabela de tokens
        tableModelTokens.setRowCount(0);

        String conteudo = txtCodigo.getText();
        StringReader sr = new StringReader(conteudo);

        // Limpa a lista de erros antes de começar a compilação
        compiladorCachacaScript.listaErros.clear();
        compiladorCachacaScript.linhasComErros.clear();

        // 1. Fase Léxica: Extração e listagem de todos os tokens na tabela
        try {
            StringReader lexReader = new StringReader(conteudo);
            SimpleCharStream lexStream = new SimpleCharStream(lexReader, 1, 1);
            compiladorCachacaScriptTokenManager lexManager = new compiladorCachacaScriptTokenManager(lexStream);
            
            Token tok = lexManager.getNextToken();
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
                tok = lexManager.getNextToken();
            }
        } catch (TokenMgrError lexError) {
            // Os erros léxicos serão capturados e reportados pelo compilador principal
        } catch (Exception ignored) {}

        // 2. Fase Sintática e AST
        if (parser == null) {
            parser = new compiladorCachacaScript(sr);
        } else {
            compiladorCachacaScript.ReInit(sr);
        }

        try {
            txtSaida.append("Lendo programa CachaçaScript da tela...\n");
            
            // Execute parser and get AST root node
            ASTNode astRoot = compiladorCachacaScript.Programa();
            
            // Verifica se houve erros recuperados
            if (compiladorCachacaScript.listaErros.isEmpty()) {
                txtSaida.append("Sucesso: Programa sintaticamente correto.\n");
            } else {
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
}
