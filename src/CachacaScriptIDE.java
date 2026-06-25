import javax.swing.*;
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
        frame = new JFrame("CachaçaScript IDE - Edição com Árvore Sintática");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(900, 650);
        frame.setLayout(new BorderLayout());

        // Code Editor
        txtCodigo = new JTextArea();
        txtCodigo.setFont(new Font("Consolas", Font.PLAIN, 14));
        txtCodigo.setTabSize(4);
        JScrollPane scrollCodigo = new JScrollPane(txtCodigo);
        
        // AST JTree
        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode("Árvore Sintática (AST)");
        treeAST = new JTree(rootNode);
        treeAST.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        JScrollPane scrollTree = new JScrollPane(treeAST);

        // Console Output
        txtSaida = new JTextArea();
        txtSaida.setEditable(false);
        txtSaida.setFont(new Font("Consolas", Font.PLAIN, 13));
        txtSaida.setBackground(new Color(245, 245, 245));
        JScrollPane scrollSaida = new JScrollPane(txtSaida);

        // Top Horizontal Split (Code Editor on left, AST on right)
        JSplitPane topSplitPane = new JSplitPane(
            JSplitPane.HORIZONTAL_SPLIT,
            scrollCodigo,
            scrollTree
        );
        topSplitPane.setDividerLocation(500);
        topSplitPane.setResizeWeight(0.7);

        // Main Vertical Split (Editor+AST on top, Console on bottom)
        JSplitPane mainSplitPane = new JSplitPane(
            JSplitPane.VERTICAL_SPLIT,
            topSplitPane,
            scrollSaida
        );
        mainSplitPane.setDividerLocation(420);
        mainSplitPane.setResizeWeight(0.6);

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

        // Load a default test structure if we can, or just start empty
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

    private void executarCompilacao() {
        txtSaida.setText("");
        String conteudo = txtCodigo.getText();
        StringReader sr = new StringReader(conteudo);

        if (parser == null) {
            parser = new compiladorCachacaScript(sr);
        } else {
            compiladorCachacaScript.ReInit(sr);
        }

        try {
            txtSaida.append("Lendo programa CachaçaScript da tela...\n");
            
            // Execute parser and get AST root node
            ASTNode astRoot = compiladorCachacaScript.Programa();
            
            txtSaida.append("Sucesso: Programa sintaticamente correto.\n");
            
            // Populate Swing Tree with the AST
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
            treeAST.setModel(new DefaultTreeModel(new DefaultMutableTreeNode("Sem Árvore (Erro Sintático)")));
            txtSaida.append("🍺 Opa! Parece que a cachaça subiu...\n");
            Token t = e.currentToken != null ? e.currentToken.next : null;
            if (t != null) {
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
        }
    }
}
