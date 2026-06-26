import java.util.List;

public class CodeGenerator {
    private static ASTNode rootNode;

    public static String generate(ASTNode root) {
        rootNode = root;
        return visit(root, 0, false);
    }

    private static String indent(int level) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < level; i++) {
            sb.append("  ");
        }
        return sb.toString();
    }

    private static String visit(ASTNode node, int level, boolean inForHeader) {
        if (node == null) return "";
        String label = node.getLabel();
        List<ASTNode> children = node.getChildren();

        if (label.startsWith("Programa ")) {
            StringBuilder sb = new StringBuilder();
            sb.append("#include <stdio.h>\n");
            sb.append("#include <stdbool.h>\n");
            sb.append("#include <string.h>\n\n");
            
            // Visit functions (children.get(0))
            if (children.size() > 0) {
                sb.append(visit(children.get(0), level, false));
            }
            
            // Visit main block (children.get(1))
            if (children.size() > 1) {
                sb.append("int main() {\n");
                ASTNode mainBlock = children.get(1);
                if (mainBlock.getChildren().size() > 0) {
                    ASTNode cmds = mainBlock.getChildren().get(0);
                    for (ASTNode cmd : cmds.getChildren()) {
                        sb.append(visit(cmd, level + 1, false));
                    }
                }
                sb.append("  return 0;\n");
                sb.append("}\n");
            }
            return sb.toString();
        }

        if (label.equals("Lista de Funções")) {
            StringBuilder sb = new StringBuilder();
            for (ASTNode child : children) {
                sb.append(visit(child, level, false)).append("\n");
            }
            return sb.toString();
        }

        if (label.startsWith("Função (alambique):")) {
            String funcName = label.substring("Função (alambique):".length()).trim();
            ASTNode retType = children.get(0);
            ASTNode params = children.get(1);
            ASTNode body = children.get(2);

            String cRetType = getCType(retType.getLabel());
            String cParams = visit(params, level, false);
            String cBody = visit(body, level, false);

            return cRetType + " " + funcName + "(" + cParams + ") " + cBody;
        }

        if (label.equals("Parâmetros")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(visit(children.get(i), level, false));
            }
            return sb.toString();
        }

        if (label.startsWith("Parâmetro:")) {
            String paramName = label.substring("Parâmetro:".length()).trim();
            ASTNode type = children.get(0);
            return getCType(type.getLabel()) + " " + paramName;
        }

        if (label.equals("Parâmetros: Nenhum")) {
            return "";
        }

        if (label.startsWith("Bloco ")) {
            StringBuilder sb = new StringBuilder();
            sb.append("{\n");
            if (children.size() > 0) {
                sb.append(visit(children.get(0), level + 1, false));
            }
            sb.append(indent(level)).append("}\n");
            return sb.toString();
        }

        if (label.equals("Comandos") || label.equals("Lista Itens") || label.equals("Lista de Expressões") || label.equals("Lista de Destinos")) {
            StringBuilder sb = new StringBuilder();
            for (ASTNode child : children) {
                sb.append(visit(child, level, inForHeader));
            }
            return sb.toString();
        }

        if (label.equals("Declaração")) {
            ASTNode typeNode = children.get(0);
            ASTNode listDecl = children.get(1);
            String cType = getCType(typeNode.getLabel());

            StringBuilder sb = new StringBuilder();
            sb.append(indent(level)).append(cType).append(" ");
            
            for (int i = 0; i < listDecl.getChildren().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(visit(listDecl.getChildren().get(i), 0, true));
            }
            sb.append(";\n");
            return sb.toString();
        }

        if (label.startsWith("Variável:")) {
            String varName = label.substring("Variável:".length()).trim();
            if (children.size() > 0) {
                ASTNode init = children.get(0); // Atribuição Inicial
                return varName + " = " + visit(init, level, false);
            }
            return varName;
        }

        if (label.equals("Atribuição Inicial (engarrafar)")) {
            return visit(children.get(0), level, false);
        }

        if (label.startsWith("Atribuição (engarrafar) para:")) {
            String varName = label.substring("Atribuição (engarrafar) para:".length()).trim();
            String exprVal = visit(children.get(0), level, false);
            StringBuilder sb = new StringBuilder();
            sb.append(indent(level)).append(varName).append(" = ").append(exprVal);
            if (!inForHeader) {
                sb.append(";\n");
            }
            return sb.toString();
        }

        if (label.startsWith("Chamada de Função:")) {
            String funcName = label.substring("Chamada de Função:".length()).trim();
            String args = visit(children.get(0), level, false);
            StringBuilder sb = new StringBuilder();
            if (!inForHeader && level > 0) {
                sb.append(indent(level));
            }
            sb.append(funcName).append("(").append(args).append(")");
            if (!inForHeader && level > 0) {
                sb.append(";\n");
            }
            return sb.toString();
        }

        if (label.equals("Argumentos")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < children.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(visit(children.get(i), level, false));
            }
            return sb.toString();
        }

        if (label.equals("Impressão (serveNoCopo)")) {
            ASTNode exprList = children.get(0);
            StringBuilder format = new StringBuilder();
            StringBuilder args = new StringBuilder();
            
            for (int i = 0; i < exprList.getChildren().size(); i++) {
                ASTNode expr = exprList.getChildren().get(i);
                String type = getExpressionType(expr);
                
                if (type.equals("int")) {
                    format.append("%d ");
                } else if (type.equals("float") || type.equals("double")) {
                    format.append("%f ");
                } else if (type.equals("bool")) {
                    format.append("%s ");
                } else {
                    format.append("%s ");
                }
                
                if (i > 0) args.append(", ");
                
                String cExpr = visit(expr, level, false);
                if (type.equals("bool")) {
                    args.append("(").append(cExpr).append(" ? \"simPatrao\" : \"nemAPau\")");
                } else {
                    args.append(cExpr);
                }
            }
            
            format.append("\\n");
            
            StringBuilder sb = new StringBuilder();
            sb.append(indent(level)).append("printf(\"").append(format.toString()).append("\"");
            if (args.length() > 0) {
                sb.append(", ").append(args.toString());
            }
            sb.append(");\n");
            return sb.toString();
        }

        if (label.equals("Leitura (pedeNoBalcao)")) {
            ASTNode destList = children.get(0);
            StringBuilder sb = new StringBuilder();
            for (ASTNode dest : destList.getChildren()) {
                String varName = dest.getLabel().substring("Variável:".length()).trim();
                String type = lookupVariableType(varName, rootNode);
                
                sb.append(indent(level));
                if (type != null && type.equals("string")) {
                    sb.append("scanf(\"%s\", ").append(varName).append(");\n");
                } else if (type != null && type.equals("float")) {
                    sb.append("scanf(\"%f\", &").append(varName).append(");\n");
                } else if (type != null && type.equals("double")) {
                    sb.append("scanf(\"%lf\", &").append(varName).append(");\n");
                } else {
                    sb.append("scanf(\"%d\", &").append(varName).append(");\n");
                }
            }
            return sb.toString();
        }

        if (label.startsWith("Condicional (seDerBoa)")) {
            ASTNode cond = children.get(0);
            ASTNode thenBloc = children.get(1);
            StringBuilder sb = new StringBuilder();
            sb.append(indent(level)).append("if (").append(visit(cond, level, false)).append(") ").append(visit(thenBloc, level, false));
            if (children.size() > 2) {
                ASTNode elseNode = children.get(2);
                sb.append(visit(elseNode, level, false));
            }
            return sb.toString();
        }

        if (label.startsWith("Senão (seDerRuim)")) {
            ASTNode elseBloc = children.get(0);
            return indent(level) + "else " + visit(elseBloc, level, false);
        }

        if (label.equals("Laço Enquanto (enquantoTemCana)")) {
            ASTNode cond = children.get(0);
            ASTNode body = children.get(1);
            return indent(level) + "while (" + visit(cond, level, false) + ") " + visit(body, level, false);
        }

        if (label.equals("Repita (tomaUma ... ateFicarTonto)")) {
            ASTNode body = children.get(0);
            ASTNode cond = children.get(1);
            return indent(level) + "do " + visit(body, level, false) + indent(level) + "while (!(" + visit(cond, level, false) + "));\n";
        }

        if (label.equals("Laço Para (rodada)")) {
            ASTNode init = children.get(0);
            ASTNode cond = children.get(1);
            ASTNode post = children.get(2);
            ASTNode body = children.get(3);

            String cInit = visit(init, 0, true).trim();
            if (cInit.endsWith(";")) cInit = cInit.substring(0, cInit.length() - 1);
            
            String cCond = visit(cond, 0, true).trim();
            
            String cPost = visit(post, 0, true).trim();
            if (cPost.endsWith(";")) cPost = cPost.substring(0, cPost.length() - 1);

            return indent(level) + "for (" + cInit + "; " + cCond + "; " + cPost + ") " + visit(body, level, false);
        }

        if (label.equals("Inicialização: Vazia") || label.equals("Condição: Vazia") || label.equals("Pós-execução: Vazia")) {
            return "";
        }

        if (label.equals("Inicialização (Declaração)")) {
            ASTNode typeNode = children.get(0);
            ASTNode listDecl = children.get(1);
            String cType = getCType(typeNode.getLabel());
            StringBuilder sb = new StringBuilder();
            sb.append(cType).append(" ");
            for (int i = 0; i < listDecl.getChildren().size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(visit(listDecl.getChildren().get(i), 0, true));
            }
            return sb.toString();
        }

        if (label.equals("Retorno (devolveDose)")) {
            StringBuilder sb = new StringBuilder();
            sb.append(indent(level)).append("return");
            if (children.size() > 0) {
                sb.append(" ").append(visit(children.get(0), level, false));
            }
            sb.append(";\n");
            return sb.toString();
        }

        if (label.startsWith("Operação Lógica:") || label.startsWith("Comparação:") || label.startsWith("Operação Aritmética:")) {
            String op = label.substring(label.indexOf(":") + 1).trim();
            String left = visit(children.get(0), level, false);
            String right = visit(children.get(1), level, false);
            return "(" + left + " " + op + " " + right + ")";
        }

        if (label.startsWith("Operação Unária:")) {
            String op = label.substring(label.indexOf(":") + 1).trim();
            String child = visit(children.get(0), level, false);
            return "(" + op + child + ")";
        }

        if (label.startsWith("Literal Real:") || label.startsWith("Literal Inteiro:")) {
            return label.substring(label.indexOf(":") + 1).trim();
        }

        if (label.startsWith("Literal Booleano:")) {
            String val = label.substring(label.indexOf(":") + 1).trim();
            return val.equals("simPatrao") ? "true" : "false";
        }

        if (label.startsWith("Literal Rótulo:")) {
            return label.substring(label.indexOf(":") + 1).trim();
        }

        if (label.startsWith("Referência de Variável:")) {
            return label.substring("Referência de Variável:".length()).trim();
        }

        if (label.equals("Expressão Agrupada")) {
            return "(" + visit(children.get(0), level, false) + ")";
        }

        return "";
    }

    private static String getCType(String typeLabel) {
        String t = typeLabel.substring(typeLabel.indexOf(":") + 1).trim();
        if (t.equals("doseInteira")) return "int";
        if (t.equals("doseQuebrada")) return "float";
        if (t.equals("taNoGrau")) return "bool";
        if (t.equals("rotulo")) return "char*";
        if (t.equals("garrafa (var)")) return "double";
        if (t.equals("semDose (void)")) return "void";
        return "int";
    }

    private static String getExpressionType(ASTNode expr) {
        String label = expr.getLabel();
        if (label.startsWith("Literal Inteiro:")) return "int";
        if (label.startsWith("Literal Real:")) return "float";
        if (label.startsWith("Literal Booleano:")) return "bool";
        if (label.startsWith("Literal Rótulo:")) return "string";
        if (label.startsWith("Referência de Variável:")) {
            String varName = label.substring("Referência de Variável:".length()).trim();
            String type = lookupVariableType(varName, rootNode);
            return type != null ? type : "int";
        }
        if (label.startsWith("Comparação:") || label.startsWith("Operação Lógica:")) return "bool";
        if (label.startsWith("Operação Aritmética:")) {
            for (ASTNode child : expr.getChildren()) {
                if (getExpressionType(child).equals("float")) return "float";
            }
            return "int";
        }
        return "int";
    }

    private static String lookupVariableType(String varName, ASTNode node) {
        if (node == null) return null;
        if (node.getLabel().equals("Declaração")) {
            ASTNode typeNode = node.getChildren().get(0);
            ASTNode listDecl = node.getChildren().get(1);
            if (declaresVariable(listDecl, varName)) {
                String t = typeNode.getLabel().substring(typeNode.getLabel().indexOf(":") + 1).trim();
                if (t.equals("doseInteira")) return "int";
                if (t.equals("doseQuebrada")) return "float";
                if (t.equals("taNoGrau")) return "bool";
                if (t.equals("rotulo")) return "string";
                if (t.equals("garrafa (var)")) return "double";
            }
        }
        if (node.getLabel().startsWith("Parâmetro:")) {
            String paramName = node.getLabel().substring("Parâmetro:".length()).trim();
            if (paramName.equals(varName)) {
                ASTNode typeNode = node.getChildren().get(0);
                String t = typeNode.getLabel().substring(typeNode.getLabel().indexOf(":") + 1).trim();
                if (t.equals("doseInteira")) return "int";
                if (t.equals("doseQuebrada")) return "float";
                if (t.equals("taNoGrau")) return "bool";
                if (t.equals("rotulo")) return "string";
                if (t.equals("garrafa (var)")) return "double";
            }
        }
        for (ASTNode child : node.getChildren()) {
            String type = lookupVariableType(varName, child);
            if (type != null) return type;
        }
        return null;
    }

    private static boolean declaresVariable(ASTNode listDecl, String varName) {
        for (ASTNode item : listDecl.getChildren()) {
            String name = item.getLabel().substring("Variável:".length()).trim();
            if (name.equals(varName)) return true;
        }
        return false;
    }
}
