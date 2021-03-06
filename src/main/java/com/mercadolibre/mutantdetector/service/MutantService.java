package com.mercadolibre.mutantdetector.service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.mercadolibre.mutantdetector.models.dao.IDnaDao;
import com.mercadolibre.mutantdetector.models.dto.DnaDTO;
import com.mercadolibre.mutantdetector.models.dto.StatDTO;
import com.mercadolibre.mutantdetector.models.entity.Dna;
import com.mercadolibre.mutantdetector.models.entity.DnaType;

/**
 * MutantService implementacion.
 * 
 */
@Service
public class MutantService implements IMutantService {

	public static final String[] DNA_WORDS = { "A", "T", "C", "G" };
	public static final String DNA_NULL = "Error, la secuencia de adn está vacia o es null:' ";
	public static final String DNA_ELEMENT_NULL = "Error, uno de los elementos de la secuencia de adn es null:' ";
	public static final String DNA_INVALID_WORD = "Error, la secuencia de adn tiene una letra invalida:' ";
	public static final String DNA_DATA_INTEGRITY_ERROR = "Error, el adn mutante que se quiere ingresar ya está en la DB'";
	public static final String INVALID_SEQUENCE = "Error, la matriz no es simetrica' ";
	public static final String GO_DOWN = "DOWN";
	public static final String GO_RIGTH = "RIGHT";
	public static final String GO_RIGTH_DIAGONAL = "RIGHT_DIAGONAL";
	public static final String GO_LEFT_DIAGONAL = "LEFT_DIAGONAL";
	public static final Integer COINCIDENCE = 3;
	public static final Long MUTANT_ID = 2L;
	public static final Long HUMAN_ID = 1L;
	private static final Logger logger = LoggerFactory.getLogger(MutantService.class);

	@Autowired
	IDnaDao dnaDao;

	Integer count;

	/**
	 * <p>
	 * Metodo que se utiliza para comprobar si una determinada secuencia de ADN
	 * pertenece a un mutante
	 * </p>
	 * 
	 * @param dna es el dto que tiene dentro la secuencia de adn se pensó asi para
	 *            incorporar nuevos elementos en el futuro
	 * @return retorna un booleano que permite saber si es mutante o no
	 * 
	 */
	@Override
	public Boolean isMutant(DnaDTO dna) {
		String[] sequence = dna.getDna();
		String[][] matriz = createMatriz(sequence);
		Boolean isMutant = deepDna(matriz);
		saveDna(dna, isMutant);
		return isMutant;
	}

	/**
	 * <p>
	 * Metodo que se utiliza para crear una matriz de dos dimensiones para una mejor
	 * manipulacion de los datos.Además verifica que sea una secuencia de ADN valida
	 * (A,T,C,G), que sea simetrica y que no esté vacia. Tambien se evalua si un
	 * elemento dentro del array es null.
	 * </p>
	 * 
	 * @param dna es un array donde cada elemento es una parte de la secuencia del
	 *            ADN
	 * @return retorna una matriz de dos direcciones
	 * 
	 */
	private String[][] createMatriz(String[] dna) {
		logger.info("Creando matriz bidimensional...");
		if (dna == null || dna.length == 0)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DNA_NULL);
		Boolean containNull = Arrays.stream(dna).allMatch(Objects::nonNull);
		if (Boolean.TRUE.equals(!containNull))
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DNA_ELEMENT_NULL);
		if (dna.length != dna[0].length())
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_SEQUENCE);
		Integer n = dna[0].length();
		String[][] dnaMatriz = new String[n][n];
		for (int k = 0; k < n; k++) {
			for (int l = 0; l < n; l++) {
				String row = dna[k];
				String[] matrizElements = row.split("");
				if (!Arrays.stream(DNA_WORDS).anyMatch(matrizElements[l]::equals))
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, DNA_INVALID_WORD + matrizElements[l]);
				if (matrizElements.length != n)
					throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_SEQUENCE);
				dnaMatriz[k][l] = matrizElements[l];
			}
		}
		logger.info("Matriz bidimensional creada con exito");
		return dnaMatriz;
	}

	/**
	 * <p>
	 * Metodo que se utiliza para poder escanear de manera completa la matriz desde
	 * aquí se llama al metodo recursivo
	 * </p>
	 * 
	 * @param matriz matriz a recorrer
	 * 
	 * @return retorna un booleano que permite saber si es mutante o no
	 * 
	 */
	private Boolean deepDna(String[][] matriz) {
		logger.info("Inicia recorrido matriz");
		Integer mutantDna = 0;
		Integer n = matriz[0].length;
		for (int x = 0; x < n; x++) {
			for (int y = 0; y < n; y++) {
				this.count = 0;
				if (Boolean.TRUE.equals(recursiveScan(matriz, GO_RIGTH, x, y))) {
					mutantDna++;
					disableElements(matriz, GO_RIGTH, x, y);
				}
				this.count = 0;
				if (Boolean.TRUE.equals(recursiveScan(matriz, GO_DOWN, x, y))) {
					mutantDna++;
					disableElements(matriz, GO_DOWN, x, y);
				}
				this.count = 0;
				if (Boolean.TRUE.equals(recursiveScan(matriz, GO_RIGTH_DIAGONAL, x, y))) {
					mutantDna++;
					disableElements(matriz, GO_RIGTH_DIAGONAL, x, y);

				}
				this.count = 0;
				if (Boolean.TRUE.equals(recursiveScan(matriz, GO_LEFT_DIAGONAL, x, y))) {
					mutantDna++;
					disableElements(matriz, GO_LEFT_DIAGONAL, x, y);
				}
			}
		}
		logger.info("Finaliza recorrido matriz");
		return sequenceCount(mutantDna);
	}

	/**
	 * <p>
	 * Metodo que utiliza la recursividad para verificar desde un punto determinado
	 * si hay una secuencia de 4 letras iguales (tres coincidencias)
	 * </p>
	 * 
	 * @param dna matriz de dos dimenciones que va a ser recorrida
	 * @param description description se utiliza para indicar la direccion en la que será
	 *            recorrida
	 * @param x   posicion donde comienza la recursividad (matriz[x][])
	 * @param y   posicion donde comienza la recursividad (matriz[][y])
	 * 
	 * @return retorna la cantidad de veces que se ejecutó la recursivodad
	 *         (coincidencias)
	 * 
	 */
	private Boolean recursiveScan(String[][] matriz, String description, int x, int y) {
		String currentElement = getElement(matriz, x, y);
		if (description.equals(GO_DOWN))
			x += 1;
		else if (description.equals(GO_RIGTH))
			y += 1;
		else if (description.equals(GO_RIGTH_DIAGONAL)) {
			x += 1;
			y += 1;
		} else if (description.equals(GO_LEFT_DIAGONAL)) {
			x += 1;
			y -= 1;
		}
		String element = getElement(matriz, x, y);
		if (currentElement.equals(element)) {
			this.count++;
			if (count.equals(COINCIDENCE))
				return true;
			return recursiveScan(matriz, description, x, y);
		}
		logger.info("Finaliza recursividad");
		return false;
	}

	/**
	 * <p>
	 * Metodo que se utiliza para obtener un elemento de una matriz determinada
	 * </p>
	 * 
	 * @param matriz matriz donde se extraerá el elemento
	 * @param x      posoción 1
	 * @param y      posoción 2
	 * 
	 * @return retorna un booleano que permite saber si es mutante o no
	 * 
	 */
	private String getElement(String[][] matriz, int x, int y) {
		String element;
		try {
			element = matriz[x][y];
		} catch (Exception e) {
			element = "";
		}
		return element;
	}

	/**
	 * <p>
	 * Metodo genera las estadisicas a partir de los datos en la DB
	 * </p>
	 * 
	 * @return retorna un dto con las estadisticas
	 * 
	 */
	@Override
	public StatDTO stats() {
		logger.info("Analizando estadisticas");
		List<Dna> allDna = (List<Dna>) dnaDao.findAll();
		Long humans = 0L;
		Long mutants = 0L;
		for (Dna d : allDna) {
			Long type = d.getDnaType().getId();
			if (type.equals(HUMAN_ID))
				humans++;
			if (type.equals(MUTANT_ID))
				mutants++;
		}
		Float ratio;
		if (humans == null || humans == 0)
			ratio = (float) mutants;
		else
			ratio = ((float) ((float) mutants / (float) humans));
		StatDTO stat = new StatDTO();
		stat.setCountHumanDna(humans);
		stat.setCountMutantDna(mutants);
		stat.setRatio(ratio);
		logger.info("finaliza estadisticas");
		return stat;
	}

	/**
	 * <p>
	 * Metodo que se utiliza para guardar la secuencia de ADN independientemente si
	 * es mutante o no
	 * </p>
	 * 
	 * @param dna      secuencia de ADN
	 * @param isMutant parametro que permite determinar que tipo de ADN es *
	 */
	private void saveDna(DnaDTO dna, Boolean isMutant) {
		logger.info("Guardando adn...");
		List<String> list = Arrays.asList(dna.getDna());
		String joinedString = String.join("", list);
		Dna dnaEntity = new Dna();
		dnaEntity.setSequence(joinedString);
		DnaType dnaType = new DnaType();
		if (isMutant) {
			dnaType.setId(MUTANT_ID);
			dnaEntity.setDnaType(dnaType);
		} else {
			dnaType.setId(HUMAN_ID);
			dnaEntity.setDnaType(dnaType);

		}
		try {
			dnaDao.save(dnaEntity);
			logger.info("ADN guardado con exito");
		} catch (DataIntegrityViolationException e) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, DNA_DATA_INTEGRITY_ERROR);
		}
	}

	/**
	 * <p>
	 * Metodo que utiliza la deshabilitar los elementos de la matriz cuando se
	 * encuentra una secuencia mutante. Al deshabilitar los elementos que componen la
	 * secuencia de cuatro letras (se deshabilita cambiando laa letra) nos
	 * aseguramos que no se utilice dentro de otra secuencia
	 * </p>
	 * 
	 * @param dna       matriz de dos dimenciones donde 4 elementos van a ser
	 *                  deshabilitados
	 * @param direction se utiliza para indicar la direccion en la que se encuetra
	 *                  la secuencia
	 * @param x         posicion donde comienza la recursividad (matriz[x][])
	 * @param y         posicion donde comienza la recursividad (matriz[][y])
	 * 
	 * 
	 */
	private void disableElements(String[][] matriz, String direction, int x, int y) {
		if (direction.equals(GO_RIGTH)) {
			for (int j = 0; j <= COINCIDENCE; j++) {
				matriz[x][y + j] = "H";
			}
		}
		if (direction.equals(GO_DOWN)) {
			for (int j = 0; j <= COINCIDENCE; j++) {
				matriz[x + j][y] = "V";
			}
		}
		if (direction.equals(GO_RIGTH_DIAGONAL)) {
			for (int j = 0; j <= COINCIDENCE; j++) {
				matriz[x + j][y + j] = "D";
			}
		}
		if (direction.equals(GO_LEFT_DIAGONAL)) {
			for (int j = 0; j <= COINCIDENCE; j++) {
				matriz[x + j][y - j] = "X";
			}
		}
	}

	private Boolean sequenceCount(Integer mutantDna) {
		if (mutantDna >= 2)
			return true;
		else
			return false;
	}

}
