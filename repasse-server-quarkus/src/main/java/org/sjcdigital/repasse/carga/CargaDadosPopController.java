package org.sjcdigital.repasse.carga;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.sjcdigital.repasse.model.base.DadosMunicipio;
import org.sjcdigital.repasse.model.base.Municipio;
import org.sjcdigital.repasse.service.impl.DadosMunicipioService;
import org.sjcdigital.repasse.service.impl.MunicipioService;
import org.sjcdigital.repasse.utils.TextoUtils;

import static javax.transaction.Transactional.TxType.NEVER;
import static javax.transaction.Transactional.TxType.REQUIRES_NEW;

/**
 * 
 * Realiza a carga dos dados de população. A fonte dos dados é o site do IBGE
 * 
 * @author wsiqueir
 *
 */
@RequestScoped
@Transactional(REQUIRES_NEW)
public class CargaDadosPopController {

	final String ARQUIVOS_POP[] = { "/dados/dados_pop_2009_2011.csv",
			"/dados/dados_pop_2012_2014.csv", "/dados/dados_pop_2015.csv" };
	
	final String SEPARADOR = ";";
	
	@Inject
	MunicipioService municipioService;
	
	@Inject
	DadosMunicipioService dadosMunicipioService;
	
	@Inject
	Logger logger;

    @Transactional(NEVER)
	public String fazCargaDadosPopulacao() throws IOException {
		logger.info("Iniciando carga de dados para população"); 
		StringBuffer relatorioFinal = new StringBuffer("Carga iniciada em: " + new Date());
		LinkedList<String> naoEncontrados = new LinkedList<>();
		Set<Integer> anos = new HashSet<>();
		for (int i = 0; i < ARQUIVOS_POP.length; i++) {	
			InputStream is = getClass().getResourceAsStream(ARQUIVOS_POP[i]);
			Path arquivo = Files.createTempFile("pop", "");
			Files.write(arquivo, IOUtils.toByteArray(is));
			String nomeColunas[] = Files.lines(arquivo).findFirst().get()
					.split(SEPARADOR);
			Files.lines(arquivo).skip(1).forEach(l -> {
				String[] col = l.split(SEPARADOR);
				String uf = col[0];
				String nomeCidade = TextoUtils.transformaNomeCidade(col[1]);
				Map<Integer, Long> anosPop = new HashMap<>();
				for(int j = 2; j < col.length; j++) {
					int ano = Integer.parseInt(nomeColunas[j]);
					long pop = Long.parseLong(col[j]);
					anosPop.put(ano, pop);
					anos.add(ano);
				}
				try {
					insereDados(uf, nomeCidade, anosPop);
				} catch (Exception  e) {
					naoEncontrados.add("\"" + nomeCidade  +  "\" - \"" + uf + "\"" );
				}
			});
			Files.delete(arquivo);
			relatorioFinal.append("O seguintes anos foram carregados: <br />");
			relatorioFinal.append(anos.stream().map(String::valueOf).collect(Collectors.joining(",")));
			relatorioFinal.append("<br />erro ao inserir dados para os seguintes municipio: (provavelmente por incompatibilidades entre os dados): <br />");
			relatorioFinal.append(naoEncontrados.stream().collect(Collectors.joining("<br/>")));
			logger.info("Fim carga de dados para população"); 
		}
		return relatorioFinal.toString();
	}

    @Transactional(REQUIRES_NEW)
	public void insereDados(String uf, String nome, Map<Integer, Long> anosPop) {
			Municipio m  = municipioService.buscaPorNomeEEstado(uf, nome);
			anosPop.entrySet().forEach(e -> {
				int ano = e.getKey();
				long pop = e.getValue();
				DadosMunicipio dadosMunicipio = dadosMunicipioService.buscaPorAnoMunicipioOuCria(ano, m);
				dadosMunicipio.setPopulacao(pop);
				dadosMunicipioService.atualizar(dadosMunicipio);
			});
	}
}